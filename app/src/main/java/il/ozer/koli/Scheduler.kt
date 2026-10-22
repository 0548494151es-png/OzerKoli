package il.ozer.koli

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** כל התזמונים עוברים דרך AlarmManager של המערכת: האפליקציה לא רצה ברקע. */
object Scheduler {
    private fun pi(c: Context, req: Int, type: String, id: Int, title: String, create: Boolean): PendingIntent? {
        val i = Intent(c, AlarmReceiver::class.java)
            .setAction("ozer.$type.$req")
            .putExtra("type", type).putExtra("id", id).putExtra("title", title)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(c, req, i, flags)
    }

    fun set(c: Context, req: Int, atMs: Long, type: String, id: Int, title: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, req, type, id, title, true)!!
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
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
        scheduleLessons(c)
        Shabbat.schedule(c)
        Geo.registerAll(c)
    }
}
