package il.ozer.koli

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface Ui {
    fun say(text: String)
    /** שואל שאלת כן/לא. שום פעולה רגישה לא מתבצעת לפני onYes. */
    fun confirm(text: String, onYes: () -> Unit)
    fun choose(text: String, options: List<String>, onPick: (Int) -> Unit)
    fun requestPerms(perms: Array<String>, then: () -> Unit)
}

class Brain(private val c: Context, private val ui: Ui) {
    /** תזכורת או אירוע שחסרה להם שעה: ממתינים לתשובה. */
    private var awaiting: Pair<String, String>? = null // kind to title

    private fun has(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun handle(raw: String) {
        val pending = awaiting
        if (pending != null) {
            awaiting = null
            val w = Parser.parseWhen(Parser.normalize(raw))
            if (w != null) { addItem(pending.first, pending.second, w.ms); return }
            ui.say("לא הבנתי את השעה, ביטלתי.")
            return
        }
        run(Parser.parse(raw))
    }

    fun run(cmd: Cmd) {
        when (cmd) {
            is Cmd.Call -> call(cmd.name)
            is Cmd.SetAlarm -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, cmd.min)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    c.startActivity(i)
                    ui.say("קבעתי שעון מעורר ל-" + String.format(Locale.US, "%02d:%02d", cmd.hour, cmd.min))
                } catch (e: Exception) { ui.say("לא הצלחתי לקבוע שעון מעורר באפליקציית השעון.") }
            }
            is Cmd.SetTimer -> {
                Scheduler.startTimer(c, cmd.seconds, "טיימר")
                ui.say("הטיימר התחיל: " + spoken(cmd.seconds))
            }
            is Cmd.Remind -> {
                if (cmd.timeMs != null) addItem("reminder", cmd.text, cmd.timeMs)
                else { awaiting = "reminder" to cmd.text; ui.say("מתי להזכיר לך?") }
            }
            is Cmd.AddEvent -> {
                if (cmd.timeMs != null) addItem("event", cmd.title, cmd.timeMs)
                else { awaiting = "event" to cmd.title; ui.say("מתי האירוע?") }
            }
            is Cmd.GeoRemind -> geoRemind(cmd)
            is Cmd.SavePlace -> savePlace(cmd.name)
            is Cmd.ShowEvents -> showEvents(cmd.dayOffset)
            is Cmd.CandleTime -> {
                Store.setInt(c, "candle_h", cmd.hour); Store.setInt(c, "candle_m", cmd.min)
                Store.setBool(c, "shabbat_on", true)
                Shabbat.schedule(c)
                ui.say("הדלקת נרות נקבעה ל-" + Shabbat.timeText(c))
            }
            Cmd.ShabbatNow -> {
                Shabbat.showCountdown(c)
                val left = ((Shabbat.nextCandle(c) - System.currentTimeMillis()) / 1000).toInt()
                ui.say("עד הדלקת נרות נשארו " + spoken(left))
            }
            is Cmd.Unknown -> ui.say("לא הבנתי: ${cmd.raw}")
        }
    }

    // ---------- חיוג עם אישור ----------
    private fun call(name: String) {
        if (!Contacts.granted(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.READ_CONTACTS)) { call(name) }
            return
        }
        val found = Contacts.find(c, name).ifEmpty { Contacts.find(c, "ל$name") }
        when {
            found.isEmpty() -> ui.say("לא מצאתי איש קשר בשם $name")
            found.size == 1 -> askCall(found[0])
            else -> ui.choose("מצאתי כמה אפשרויות. את מי לבחור?", found.map { "${it.name} (${it.number})" }) { askCall(found[it]) }
        }
    }

    private fun askCall(ct: Contact) {
        ui.confirm("להתקשר ל${ct.name}?") {
            val uri = Uri.parse("tel:" + Uri.encode(ct.number))
            val intent = if (has(Manifest.permission.CALL_PHONE)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
            try { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: Exception) { ui.say("לא הצלחתי לחייג.") }
        }
    }

    // ---------- תזכורות ויומן ----------
    private fun addItem(kind: String, title: String, ms: Long) {
        val item = Item(Store.nextId(c), title, ms, kind, "none")
        Store.saveItems(c, Store.items(c) + item)
        Scheduler.scheduleItem(c, item)
        val f = SimpleDateFormat("EEEE dd/MM בשעה HH:mm", Locale("he", "IL"))
        ui.say((if (kind == "event") "הוספתי ליומן: " else "אזכיר לך: ") + title + ", " + f.format(Date(ms)))
    }

    private fun showEvents(dayOffset: Int) {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val end = start + 86_400_000L
        val list = Store.items(c).filter { it.timeMs in start until end }.sortedBy { it.timeMs }
        val day = if (dayOffset == 0) "היום" else "מחר"
        if (list.isEmpty()) { ui.say("אין אירועים או תזכורות $day"); return }
        val f = SimpleDateFormat("HH:mm", Locale.US)
        ui.say("$day: " + list.joinToString(", ") { "${it.title} ב-${f.format(Date(it.timeMs))}" })
    }

    // ---------- מיקום ----------
    private fun norm(s: String) = s.trim().removePrefix("ה")

    private fun savePlace(name: String) {
        if (!Geo.hasFine(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { savePlace(name) }
            return
        }
        ui.say("שומר את המיקום הנוכחי כ$name...")
        Geo.saveCurrentPlace(c, name) { ok ->
            ui.say(if (ok) "שמרתי את המקום $name" else "לא הצלחתי לקבל מיקום. נסה שוב בחוץ.")
        }
    }

    private fun geoRemind(cmd: Cmd.GeoRemind) {
        val place = Store.places(c).firstOrNull { norm(it.name) == norm(cmd.place) }
        if (place == null) {
            ui.say("המקום ${cmd.place} עדיין לא שמור. כשתגיע אליו אמור: שמור מקום ${cmd.place}")
            return
        }
        if (!Geo.hasFine(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { geoRemind(cmd) }
            return
        }
        if (!Geo.hasBackground(c)) {
            ui.say("כדי שזה יעבוד כשהאפליקציה סגורה, צריך לאשר מיקום 'תמיד' בהגדרות האפליקציה (מסך בדיקת תקלות).")
        }
        Store.saveGeos(c, Store.geos(c) + GeoRem(Store.nextId(c), cmd.text, place.name))
        Geo.registerAll(c)
        ui.say("אזכיר לך ${cmd.text} כשתגיע ל${place.name}")
    }

    companion object {
        fun spoken(totalSec: Int): String {
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            val parts = mutableListOf<String>()
            if (h > 0) parts += if (h == 1) "שעה" else if (h == 2) "שעתיים" else "$h שעות"
            if (m > 0) parts += if (m == 1) "דקה" else "$m דקות"
            if (h == 0 && m == 0) parts += "$s שניות"
            return parts.joinToString(" ו")
        }
    }
}
