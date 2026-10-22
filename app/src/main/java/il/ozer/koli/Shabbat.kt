package il.ozer.koli

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ספירה לאחור שבועית לפני הדלקת נרות, לפי הזמן שהמשתמש מגדיר.
 * ההתראה מצוירת על ידי המערכת (כרונומטר יורד), בלי שירות שרץ ובלי עלות סוללה.
 */
object Shabbat {
    const val REQ_START = 9001
    const val REQ_END = 9002
    const val REQ_ASK = 9003
    const val NOTIF_ID = 9001

    fun enabled(c: Context) = Store.bool(c, "shabbat_on")
    fun hour(c: Context) = Store.int(c, "candle_h", 17)
    fun minute(c: Context) = Store.int(c, "candle_m", 30)
    fun hoursBefore(c: Context) = Store.int(c, "shabbat_before", 4)

    fun nextCandle(c: Context): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
        cal.set(Calendar.HOUR_OF_DAY, hour(c))
        cal.set(Calendar.MINUTE, minute(c))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (cal.timeInMillis <= System.currentTimeMillis() + 2000) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    fun timeText(c: Context) = String.format(Locale.US, "%02d:%02d", hour(c), minute(c))

    fun showCountdown(c: Context) {
        val candle = nextCandle(c)
        Notif.countdown(c, NOTIF_ID, "עד הדלקת נרות", "הדלקת נרות בשעה ${timeText(c)}", candle)
    }

    fun schedule(c: Context) {
        Scheduler.cancel(c, REQ_START, "shabbat_start")
        Scheduler.cancel(c, REQ_END, "shabbat_end")
        Scheduler.cancel(c, REQ_ASK, "shabbat_ask")
        if (!enabled(c)) { Notif.cancel(c, NOTIF_ID); return }
        val candle = nextCandle(c)
        val start = candle - hoursBefore(c) * 3_600_000L
        Scheduler.set(c, REQ_END, candle, "shabbat_end", REQ_END, "הגיע זמן הדלקת נרות")
        if (start <= System.currentTimeMillis()) showCountdown(c)
        else Scheduler.set(c, REQ_START, start, "shabbat_start", REQ_START, "ספירה לאחור")
        // תזכורת ביום חמישי בערב לעדכן את זמן ההדלקה לשבת
        val ask = Calendar.getInstance().apply {
            timeInMillis = candle
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0)
        }.timeInMillis
        if (ask > System.currentTimeMillis())
            Scheduler.set(c, REQ_ASK, ask, "shabbat_ask", REQ_ASK, "לעדכן את זמן הדלקת הנרות?")
    }

    fun describe(c: Context): String {
        val f = SimpleDateFormat("EEEE dd/MM HH:mm", Locale("he", "IL"))
        return f.format(Date(nextCandle(c)))
    }
}
