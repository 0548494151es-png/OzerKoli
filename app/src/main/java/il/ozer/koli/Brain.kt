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
    /** כשחסר פרט (שעה, תוכן הודעה...) ממתינים למשפט הבא ומעבירים אותו לפונקציה הזו. */
    private var next: ((String) -> Unit)? = null

    private fun has(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun handle(raw: String) {
        val n = next
        if (n != null) { next = null; n(raw); return }
        run(Parser.parse(raw, Store.places(c).map { it.name }))
    }

    private fun askTime(kind: String, title: String) {
        next = { raw ->
            val w = Parser.parseWhen(Parser.normalize(raw))
            if (w != null) addItem(kind, title, w.ms) else ui.say("לא הבנתי את השעה, ביטלתי.")
        }
    }

    fun run(cmd: Cmd) {
        when (cmd) {
            is Cmd.Call -> call(cmd.name)
            is Cmd.SetAlarm -> {
                // פותח את אפליקציית השעון עם השעה מוכנה. בלי SKIP_UI, כי בחלק מהמכשירים (למשל שיאומי) זה נתקע בשקט.
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, cmd.min)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "עוזר קולי")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val hhmm = String.format(Locale.US, "%02d:%02d", cmd.hour, cmd.min)
                try {
                    c.startActivity(i)
                    ui.say("פתחתי את השעון עם שעון מעורר ל-$hhmm. אם צריך, אשר שם.")
                } catch (e: Exception) {
                    ui.say("השעון של הטלפון לא הגיב (${e.javaClass.simpleName}). קבע ידנית ל-$hhmm.")
                }
            }
            is Cmd.SetTimer -> {
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, cmd.seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "טיימר")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (cmd.seconds in 1..86400) {
                    try {
                        c.startActivity(i)
                        ui.say("פתחתי את השעון עם טיימר ל" + spoken(cmd.seconds) + ". אם צריך, לחץ שם התחל.")
                        return
                    } catch (e: Exception) {
                        ui.say("השעון לא הגיב (${e.javaClass.simpleName}), הפעלתי טיימר פנימי.")
                    }
                }
                Scheduler.startTimer(c, cmd.seconds, "טיימר")
            }
            is Cmd.Remind -> {
                if (cmd.timeMs != null) addItem("reminder", cmd.text, cmd.timeMs)
                else { askTime("reminder", cmd.text); ui.say("מתי להזכיר לך?") }
            }
            is Cmd.AddEvent -> {
                if (cmd.timeMs != null) addItem("event", cmd.title, cmd.timeMs)
                else { askTime("event", cmd.title); ui.say("מתי האירוע?") }
            }
            is Cmd.GeoRemind -> geoRemind(cmd)
            is Cmd.Message -> message(cmd)
            is Cmd.SavePlace -> if (cmd.address != null) geocode(cmd.name, cmd.address) { ui.say("שמרתי את המקום ${cmd.name}") } else savePlace(cmd.name)
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
            is Cmd.Unknown -> ui.say("לא הבנתי: ${cmd.raw}. נסה: תתקשר ל..., תשלח הודעה ל..., תזכיר לי ... כשאגיע ל..., טיימר 10 דקות")
        }
    }

    // ---------- חיוג עם אישור ----------
    private fun call(name: String) {
        if (!Contacts.granted(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.READ_CONTACTS)) { call(name) }
            return
        }
        val found = Contacts.find(c, name).ifEmpty { Contacts.find(c, "ל$name") }
        if (found.isEmpty()) { ui.say("לא מצאתי איש קשר בשם $name"); return }
        // לחיצה על השם היא האישור: שום חיוג לא מתבצע בלי שתלחץ
        ui.choose("לחץ על השם כדי להתקשר", found.map { "${it.name} (${it.number})" }) { dial(found[it]) }
    }

    private fun dial(ct: Contact) {
        val uri = Uri.parse("tel:" + Uri.encode(ct.number))
        val intent = if (has(Manifest.permission.CALL_PHONE)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
        try { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { ui.say("לא הצלחתי לחייג.") }
    }

    // ---------- שליחת הודעה ----------
    private fun message(cmd: Cmd.Message) {
        if (!Contacts.granted(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.READ_CONTACTS)) { message(cmd) }
            return
        }
        var tokens = cmd.rest.split(" ").filter { it.isNotBlank() }
        if (tokens.firstOrNull() == "אל") tokens = tokens.drop(1)
        if (tokens.isEmpty()) { ui.say("למי לשלוח הודעה?"); return }
        for (k in minOf(3, tokens.size) downTo 1) {
            val raw = tokens.take(k).joinToString(" ")
            val names = listOf(raw.removePrefix("ל"), raw).distinct()
            val found = names.firstNotNullOfOrNull { n -> Contacts.find(c, n).ifEmpty { null } } ?: continue
            var body = tokens.drop(k).joinToString(" ")
            body = body.replace(Regex("^ש(?=(?:אני|אנחנו|אתה|את|הוא|היא|הם|הן|אגיע|אאחר)(?![א-ת]))"), "")
            if (body.isBlank()) {
                next = { text -> chooseSend(found, Parser.normalize(text), cmd.whatsapp) }
                ui.say("מה לכתוב בהודעה?")
            } else chooseSend(found, body, cmd.whatsapp)
            return
        }
        ui.say("לא מצאתי איש קשר: ${tokens.first().removePrefix("ל")}")
    }

    private fun chooseSend(found: List<Contact>, body: String, wa: Boolean?) {
        val opts = mutableListOf<Triple<Contact, Boolean, String>>()
        found.forEach { ct ->
            if (wa != true) opts.add(Triple(ct, false, "${ct.name} - הודעת SMS"))
            if (wa != false) opts.add(Triple(ct, true, "${ct.name} - וואטסאפ"))
        }
        ui.choose("ההודעה: \"$body\". לחץ על השם כדי לפתוח אותה לשליחה", opts.map { it.third }) { i ->
            val (ct, viaWa, _) = opts[i]
            sendMessage(ct, body, viaWa)
        }
    }

    private fun sendMessage(ct: Contact, body: String, viaWa: Boolean) {
        val intent = if (viaWa) {
            var d = ct.number.filter { it.isDigit() || it == '+' }
            d = if (d.startsWith("+")) d.drop(1) else if (d.startsWith("0")) "972" + d.drop(1) else d
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d?text=" + Uri.encode(body)))
        } else {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(ct.number))).putExtra("sms_body", body)
        }
        try { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { ui.say("לא הצלחתי לפתוח את אפליקציית ההודעות.") }
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
        if (cmd.text.isBlank()) {
            next = { t -> geoRemind(cmd.copy(text = Parser.normalize(t))) }
            ui.say("מה להזכיר לך כשתגיע ל${cmd.place}?")
            return
        }
        val place = Store.places(c).firstOrNull { norm(it.name) == norm(cmd.place) }
        if (place == null) { unknownPlace(cmd); return }
        if (!Geo.hasFine(c)) {
            ui.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { geoRemind(cmd) }
            return
        }
        if (!Geo.hasBackground(c)) {
            ui.say("כדי שזה יעבוד כשהאפליקציה סגורה, צריך לאשר מיקום 'תמיד' (הגדרות > בדיקת תקלות > מיקום 'תמיד').")
        }
        Store.saveGeos(c, Store.geos(c) + GeoRem(Store.nextId(c), cmd.text, place.name, cmd.leave, cmd.always))
        Geo.registerAll(c)
        val when_ = if (cmd.leave) "כשתצא מ${place.name}" else "כשתגיע ל${place.name}"
        ui.say("אזכיר לך ${cmd.text} $when_" + if (cmd.always) " (בכל פעם)" else "")
    }

    /** המקום עדיין לא שמור: מציעים לשמור את המיקום הנוכחי או לחפש לפי שם. */
    private fun unknownPlace(cmd: Cmd.GeoRemind) {
        ui.choose("המקום \"${cmd.place}\" עדיין לא שמור. מה לעשות?",
            listOf("אני נמצא שם עכשיו: שמור את המיקום", "חפש את המקום לפי שם (דורש אינטרנט)", "ביטול")) { i ->
            when (i) {
                0 -> {
                    if (!Geo.hasFine(c)) {
                        ui.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { unknownPlace(cmd) }
                    } else {
                        ui.say("שומר את המיקום הנוכחי...")
                        Geo.saveCurrentPlace(c, cmd.place) { ok ->
                            if (ok) geoRemind(cmd) else ui.say("לא הצלחתי לקבל מיקום. נסה שוב בחוץ.")
                        }
                    }
                }
                1 -> searchPlace(cmd)
                else -> ui.say("ביטלתי.")
            }
        }
    }

    private fun searchPlace(cmd: Cmd.GeoRemind) = geocode(cmd.place, cmd.place) { geoRemind(cmd) }

    /** מחפש כתובת או שם מקום (דורש אינטרנט), נותן לבחור תוצאה בלחיצה, שומר ואז ממשיך. */
    fun geocode(name: String, address: String, then: () -> Unit) {
        ui.say("מחפש את $address...")
        Thread {
            try {
                @Suppress("DEPRECATION")
                val res = android.location.Geocoder(c, Locale("he", "IL")).getFromLocationName(address, 3)
                if (res.isNullOrEmpty()) { ui.say("לא מצאתי את $address. נסה כתובת מלאה יותר, או שמור כשאתה שם."); return@Thread }
                val labels = res.map { it.getAddressLine(0) ?: address }
                ui.choose("מצאתי. לחץ על המקום הנכון", labels) { i ->
                    val r = res[i]
                    Store.savePlaces(c, Store.places(c).filter { it.name != name } + Place(name, r.latitude, r.longitude))
                    Geo.registerAll(c)
                    then()
                }
            } catch (e: Exception) {
                ui.say("החיפוש נכשל (אין אינטרנט?). עדיף לשמור את המקום כשאתה שם.")
            }
        }.start()
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
