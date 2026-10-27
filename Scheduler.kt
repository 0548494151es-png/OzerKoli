package il.ozer.koli

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** כל התזמונים עוברים דרך AlarmManager של המערכת: האפליקציה לא רצה ברקע. */
object Scheduler {
    private fun pi(c: Context, req: Int, type: String, id: Int, title: String, create: Boolean, at: Long = 0L): PendingIntent? {
        val i = Intent(c, AlarmReceiver::class.java)
            .setAction("ozer.$type.$req")
            .putExtra("type", type).putExtra("id", id).putExtra("title", title).putExtra("at", at)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(c, req, i, flags)
    }

    private fun showIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * setAlarmClock הוא סוג ההתראה האמין ביותר באנדרואיד: מתעורר גם במצב חיסכון/Doze,
     * ולא נחסם על ידי חיסכון בסוללה או "הרשאת התראות מדויקות".
     */
    fun set(c: Context, req: Int, atMs: Long, type: String, id: Int, title: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, req, type, id, title, true, atMs)!!
        var mode = "שעון מעורר"
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, showIntent(c)), p)
        } catch (e: Exception) {
            mode = "מדויק"
            try {
                val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
                else { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p); mode = "לא מדויק" }
            } catch (e2: Exception) {
                mode = "נכשל: ${e2.javaClass.simpleName}"
            }
        }
        Elog.add(c, "תוזמן ($mode): $type \"$title\" ל-${Elog.fmt(atMs)}")
    }

    fun cancel(c: Context, req: Int, type: String) {
        val p = pi(c, req, type, 0, "", false) ?: return
        c.getSystemService(AlarmManager::class.java).cancel(p)
        p.cancel()
    }

    // ---- תזכורות ויומן ----
    fun scheduleItem(c: Context, it: Item) {
        if (it.timeMs > System.currentTimeMillis()) set(c, it.id, it.timeMs, "item", it.id, it.title)
    }

    fun cancelItem(c: Context, id: Int) = cancel(c, id, "item")

    /**
     * רשת ביטחון: תזכורת שעברה ולא קפצה (למשל כי המערכת עצרה את האפליקציה)
     * מוצגת מיד כשהאפליקציה נפתחת או שהמכשיר עולה, ומסומנת כבוצעה.
     */
    fun catchUp(c: Context) {
        val now = System.currentTimeMillis()
        val list = Store.items(c)
        val late = list.filter { !it.fired && it.repeat == "none" && it.timeMs <= now && it.timeMs > now - 24 * 3_600_000L }
        if (late.isEmpty()) return
        late.forEach {
            cancelItem(c, it.id)
            Notif.alert(c, it.id, "תזכורת (באיחור)", it.title)
            Elog.add(c, "הושלמה תזכורת שהוחמצה: \"${it.title}\"")
        }
        val ids = late.map { it.id }
        Store.saveItems(c, list.map { x -> if (ids.contains(x.id)) x.copy(fired = true) else x })
    }

    // ---- טיימר: התראה בסיום + ספירה לאחור שמצוירת על ידי המערכת ----
    fun startTimer(c: Context, seconds: Int, label: String): Int {
        val id = Store.nextId(c)
        val end = System.currentTimeMillis() + seconds * 1000L
        set(c, id, end, "timer", id, label)
        Notif.countdown(c, id, label, "טיימר פועל", end)
        return id
    }

    // ---- שיעורים (מצב מורה) ----
    fun nextLesson(l: Lesson): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, l.dow)
        cal.set(Calendar.HOUR_OF_DAY, l.hour)
        cal.set(Calendar.MINUTE, l.min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MINUTE, -l.before)
        while (cal.timeInMillis <= System.currentTimeMillis() + 2000) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    fun scheduleLesson(c: Context, l: Lesson) =
        set(c, l.id, nextLesson(l), "lesson", l.id, "${l.title} בעוד ${l.before} דקות")

    fun scheduleLessons(c: Context) {
        if (!Store.bool(c, "teacher")) return
        Store.lessons(c).forEach { scheduleLesson(c, it) }
    }

    /** קורא בעת הפעלה מחדש של המכשיר או עדכון האפליקציה. */
    fun rescheduleAll(c: Context) {
        Notif.channels(c)
        Store.items(c).forEach { scheduleItem(c, it) }
        catchUp(c)
        scheduleLessons(c)
        Shabbat.schedule(c)
        Geo.registerAll(c)
    }
}
