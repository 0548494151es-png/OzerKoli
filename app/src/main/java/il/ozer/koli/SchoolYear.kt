package il.ozer.koli

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * ספירת ימי לימוד עד סוף שנת הלימודים תשפ"ז (ימים א'-ה' בלבד), לפי לוח משרד החינוך כפי שפורסם.
 * חישוב מקומי בלבד, ללא אינטרנט וללא צריכת סוללה. בשנה הבאה צריך לעדכן את התאריכים כאן.
 */
object SchoolYear {
    val END: LocalDate = LocalDate.of(2027, 6, 30)

    // ימים ללא לימודים (כולל ימי חג שחלים בהם)
    private val BREAKS = listOf(
        LocalDate.of(2026, 12, 6) to LocalDate.of(2026, 12, 12),   // חנוכה
        LocalDate.of(2027, 3, 23) to LocalDate.of(2027, 3, 24),    // פורים
        LocalDate.of(2027, 4, 13) to LocalDate.of(2027, 4, 28),    // פסח
        LocalDate.of(2027, 5, 12) to LocalDate.of(2027, 5, 12),    // יום העצמאות
        LocalDate.of(2027, 6, 10) to LocalDate.of(2027, 6, 11)     // שבועות
    )

    fun extraOff(c: Context): List<LocalDate> =
        Store.str(c, "school_off").split(",").mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.sorted()

    fun addOff(c: Context, d: LocalDate) {
        val all = (extraOff(c) + d).distinct().sorted()
        Store.setStr(c, "school_off", all.joinToString(","))
    }

    fun removeOff(c: Context, d: LocalDate) {
        Store.setStr(c, "school_off", extraOff(c).filter { it != d }.joinToString(","))
    }

    private fun isSchoolDay(d: LocalDate, extra: List<LocalDate>): Boolean {
        if (d.dayOfWeek == DayOfWeek.FRIDAY || d.dayOfWeek == DayOfWeek.SATURDAY) return false
        if (BREAKS.any { !d.isBefore(it.first) && !d.isAfter(it.second) }) return false
        return !extra.contains(d)
    }

    /** כולל את היום אם עדיין לפני 14:00, אחרת מחר ואילך. */
    fun schoolDaysLeft(c: Context): Int {
        val now = LocalDateTime.now()
        var d = if (now.hour < 14) now.toLocalDate() else now.toLocalDate().plusDays(1)
        val extra = extraOff(c)
        var n = 0
        while (!d.isAfter(END)) {
            if (isSchoolDay(d, extra)) n++
            d = d.plusDays(1)
        }
        return n
    }

    fun calendarDaysLeft(): Long = ChronoUnit.DAYS.between(LocalDate.now(), END)

    fun summary(c: Context): String {
        val cal = calendarDaysLeft()
        if (cal < 0) return "שנת הלימודים תשפ\"ז הסתיימה."
        return "נותרו ${schoolDaysLeft(c)} ימי לימוד (ראשון עד חמישי) עד סוף שנת הלימודים ב-30 ביוני 2027, וסך הכול $cal ימים בלוח השנה."
    }
}
