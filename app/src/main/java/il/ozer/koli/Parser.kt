package il.ozer.koli

import java.util.Calendar

sealed class Cmd {
    data class Call(val name: String) : Cmd()
    data class SetAlarm(val hour: Int, val min: Int) : Cmd()
    data class SetTimer(val seconds: Int) : Cmd()
    data class Remind(val text: String, val timeMs: Long?) : Cmd()
    data class GeoRemind(val text: String, val place: String) : Cmd()
    data class Message(val rest: String, val whatsapp: Boolean?) : Cmd()
    data class AddEvent(val title: String, val timeMs: Long?) : Cmd()
    data class SavePlace(val name: String) : Cmd()
    data class ShowEvents(val dayOffset: Int) : Cmd()
    data class CandleTime(val hour: Int, val min: Int) : Cmd()
    object ShabbatNow : Cmd()
    data class Unknown(val raw: String) : Cmd()
}

data class When(val ms: Long, val rest: String, val hasClock: Boolean)

object Parser {
    private val DAYS = mapOf("ראשון" to 1, "שני" to 2, "שלישי" to 3, "רביעי" to 4, "חמישי" to 5, "שישי" to 6, "שבת" to 7)
    private val NUMS = mapOf(
        "אחת" to 1, "שתיים" to 2, "שתים" to 2, "שלוש" to 3, "ארבע" to 4, "חמש" to 5,
        "שש" to 6, "שבע" to 7, "שמונה" to 8, "תשע" to 9, "עשר" to 10
    )

    fun normalize(s: String) = s.replace(Regex("""[,!?"״]"""), " ").replace(Regex("\\s+"), " ").trim()

    // ---------- משך זמן ----------
    private val DUR = Regex(
        """(?:\d+\s*(?:שעות|שעה|דקות|דקה|שניות|שנייה)|שעה וחצי|חצי שעה|רבע שעה|שעתיים|שעה|דקה)"""
    )

    fun durationSeconds(s: String): Int {
        var t = Num.convert(s)
        var total = 0
        fun take(r: Regex, f: (MatchResult) -> Int) {
            r.findAll(t).forEach { total += f(it) }
            t = r.replace(t, " ")
        }
        take(Regex("שעה וחצי")) { 5400 }
        take(Regex("חצי שעה")) { 1800 }
        take(Regex("רבע שעה")) { 900 }
        take(Regex("שעתיים")) { 7200 }
        take(Regex("""(\d+)\s*שעות?""")) { it.groupValues[1].toInt() * 3600 }
        take(Regex("""(\d+)\s*דקות?""")) { it.groupValues[1].toInt() * 60 }
        take(Regex("""(\d+)\s*שני(?:יות|יה)""")) { it.groupValues[1].toInt() }
        take(Regex("""(?<![א-ת])שעה(?![א-ת])""")) { 3600 }
        take(Regex("""(?<![א-ת])דקה(?![א-ת])""")) { 60 }
        return total
    }

    // ---------- מועד ----------
    private val CLOCK1 = Regex("""(?<![א-ת])(?:בשעה\s*|ב[-־]?\s*)?(\d{1,2})[:.](\d{2})""")
    private val CLOCK2 = Regex("""(?<![א-ת])(?:בשעה\s*|ב[-־]\s*|ב\s+)(\d{1,2})(?:\s*ו(חצי|רבע))?(?!\d)""")
    private val CLOCK3 = Regex("""(?<![א-ת])ב(?:שעה\s+)?(אחת|שתיים|שתים|שלוש|ארבע|חמש|שש|שבע|שמונה|תשע|עשר)(?:\s+ו(חצי|רבע))?""")

    fun parseWhen(input: String): When? {
        var t = input
        val cal = Calendar.getInstance().apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val now = System.currentTimeMillis()

        // 1. בעוד X
        val tn = Num.convert(t)
        val rel = Regex("""בעוד\s+($DUR(?:\s+ו\d+\s*דקות)?)""").find(tn)
        if (rel != null) {
            val sec = durationSeconds(rel.groupValues[1])
            if (sec > 0) {
                t = tn.replace(rel.value, " ")
                return When(now + sec * 1000L, norm(t), true)
            }
        }

        // 2. יום
        var dayAdd = -1
        when {
            t.contains("מחרתיים") -> { dayAdd = 2; t = t.replace("מחרתיים", " ") }
            t.contains("מחר") -> { dayAdd = 1; t = t.replace("מחר", " ") }
            t.contains("היום") -> { dayAdd = 0; t = t.replace("היום", " ") }
            else -> {
                val m = Regex("""ביום\s+(ראשון|שני|שלישי|רביעי|חמישי|שישי|שבת)""").find(t)
                if (m != null) {
                    val target = DAYS[m.groupValues[1]]!!
                    val cur = cal.get(Calendar.DAY_OF_WEEK)
                    dayAdd = ((target - cur) + 7) % 7
                    t = t.replace(m.value, " ")
                }
            }
        }

        // 3. שעה
        var hour = -1; var min = 0
        val c1 = CLOCK1.find(t)
        if (c1 != null) {
            hour = c1.groupValues[1].toInt(); min = c1.groupValues[2].toInt(); t = t.replace(c1.value, " ")
        } else {
            val c2 = CLOCK2.find(t)
            val c3 = CLOCK3.find(t)
            val m = c2 ?: c3
            if (m != null) {
                hour = if (m == c2) m.groupValues[1].toInt() else NUMS[m.groupValues[1]]!!
                min = when (m.groupValues[2]) { "חצי" -> 30; "רבע" -> 15; else -> 0 }
                t = t.replace(m.value, " ")
            }
        }
        if (hour > 23 || min > 59) return null
        if (dayAdd < 0 && hour < 0) return null

        val explicitPm = Regex("בערב|בלילה|אחר הצהריים|אחרי הצהריים|אחה צ").containsMatchIn(input)
        val explicitAm = input.contains("בבוקר")
        t = Regex("בערב|בלילה|בבוקר|אחר הצהריים|אחרי הצהריים|בצהריים").replace(t, " ")
        val hasClock = hour >= 0
        if (hour < 0) { hour = 9; min = 0 }
        if (explicitPm && hour < 12) hour += 12

        cal.add(Calendar.DAY_OF_YEAR, if (dayAdd < 0) 0 else dayAdd)
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, min)
        if (dayAdd < 0 && cal.timeInMillis <= now) {
            // שעה בלי יום: אם עברה, מנסים אחר הצהריים ואז מחר
            if (!explicitAm && !explicitPm && hour < 12 && cal.timeInMillis + 12 * 3_600_000L > now)
                cal.add(Calendar.HOUR_OF_DAY, 12)
            else cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return When(cal.timeInMillis, norm(t), hasClock)
    }

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()

    // ---------- פקודות ----------
    fun parse(raw: String, places: List<String> = emptyList()): Cmd {
        val s = normalize(raw)

        Regex("""^(?:תתקשר|התקשר|חייג|תחייג|תתקשרי|תחייגי)\s+(?:אל\s+)?(.+)$""").find(s)?.let {
            var n = it.groupValues[1].trim()
            if (n.startsWith("ל") && n.length > 2) n = n.substring(1)
            return Cmd.Call(n)
        }

        parseGeo(s, places)?.let { return it }

        Regex("""^(?:תשלח|שלח|תשלחי|תכתוב|תכתבי|כתוב)\s+(.+)$""").find(s)?.let {
            var r = it.groupValues[1]
            if (!r.startsWith("לי ")) {
                val wa = Regex("וואטסאפ|ווטסאפ|וואצאפ|ווצאפ|whatsapp", RegexOption.IGNORE_CASE).containsMatchIn(r)
                val sms = Regex("sms|אסמס|מסרון", RegexOption.IGNORE_CASE).containsMatchIn(r)
                r = r.replace(Regex("""(?<![א-ת])(?:ב|ב-)?(?:הודעה|הודעת|טקסט|וואטסאפ|ווטסאפ|וואצאפ|ווצאפ|sms|SMS|whatsapp)(?![א-ת])"""), " ")
                r = r.replace(Regex("\\s+"), " ").trim()
                return Cmd.Message(r, if (wa) true else if (sms) false else null)
            }
        }

        if (Regex("ט[י]{1,3}מר").containsMatchIn(s)) {
            val sec = durationSeconds(s)
            if (s.contains("שבת") || s.contains("נרות")) return Cmd.ShabbatNow
            return if (sec > 0) Cmd.SetTimer(sec) else Cmd.Unknown(s)
        }

        Regex("""הדלקת נרות\s+(?:היא\s+)?(?:ב|בשעה\s*)(\d{1,2})[:.](\d{2})""").find(s)?.let {
            return Cmd.CandleTime(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        if (Regex("כמה זמן.*(שבת|נרות)").containsMatchIn(s)) return Cmd.ShabbatNow

        Regex("""^(?:שמור|תשמור)\s+(?:את\s+)?(?:המקום|המיקום)(?:\s+הנוכחי)?(?:\s+בשם|\s+כ|\s+בתור)?\s*(.+)$""").find(s)?.let {
            return Cmd.SavePlace(it.groupValues[1].trim())
        }

        if (Regex("שעון מעורר|מעורר|תעיר אותי|תעירי אותי|תעיר|השכם").containsMatchIn(s)) {
            val w = parseWhen(s)
            if (w != null && w.hasClock) {
                val c = Calendar.getInstance().apply { timeInMillis = w.ms }
                return Cmd.SetAlarm(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            }
            return Cmd.Unknown(s)
        }

        if (Regex("^(?:מה יש לי|מה האירועים|מה ביומן|מה יש ביומן)").containsMatchIn(s))
            return Cmd.ShowEvents(if (s.contains("מחר")) 1 else 0)

        Regex("""^(?:תוסיף|הוסף|תקבע|קבע|תרשום|רשום)\s+(?:ליומן\s+)?(.+)$""").find(s)?.let {
            if (s.contains("יומן") || Regex("פגישה|אירוע|ישיבה|תור").containsMatchIn(s)) {
                val w = parseWhen(it.groupValues[1])
                val title = (w?.rest ?: it.groupValues[1]).replace(Regex("^(?:אירוע|פגישה)\\s+"), "").ifBlank { "אירוע" }
                return Cmd.AddEvent(title, w?.ms)
            }
        }

        Regex("""^(?:תזכיר לי|תזכירי לי|הזכר לי)\s+(.+)$""").find(s)?.let {
            val w = parseWhen(it.groupValues[1])
            return Cmd.Remind((w?.rest ?: it.groupValues[1]).ifBlank { "תזכורת" }, w?.ms)
        }

        return Cmd.Unknown(s)
    }

    private val TRIG = Regex("""(?:כש|כאשר |ברגע ש|כשאני )(?:אגיע|מגיע|אני מגיע|אני אגיע|אהיה|אכנס|נכנס|אני נכנס|אגיע)\s*""")
    private val CMDWORDS = Regex(
        """(?<![א-ת])(?:תזכיר|תזכירי|הזכר|תשלח|שלח|תשלחי|תעשה|תעשי|תן|תיתן|תרשום)\s+לי(?:\s+(?:תזכורת|הודעה|התראה))?(?:\s+(?:על|ש))?"""
    )

    private fun parseGeo(s: String, places: List<String>): Cmd? {
        val m = TRIG.find(s) ?: return null
        val before = s.substring(0, m.range.first)
        val raw = s.substring(m.range.last + 1).trim()
        val stripped = raw.replace(Regex("^(?:לתוך\\s+|אל\\s+|ל|ב)"), "")
        fun nz(x: String) = x.trim().removePrefix("ה")
        var place: String? = null
        var restAfter = stripped
        var after = stripped
        loop@ for (p in places.sortedByDescending { it.length }) {
            for (cand in listOf(stripped, raw)) {
                val a = nz(cand)
                if (a.startsWith(nz(p))) {
                    place = p
                    restAfter = a.removePrefix(nz(p)).trim()
                    break@loop
                }
            }
        }
        if (place == null) {
            val stop = Regex("""\s+(?:תזכיר|תזכירי|הזכר|תשלח|שלח|תשלחי|תעשה|תן)\s""").find(after)
            val chunk = if (stop != null) after.substring(0, stop.range.first) else after
            val words = chunk.split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return null
            place = words.first().let { if (it == "הביתה") "בית" else it }
            restAfter = if (stop != null) after.substring(stop.range.first) else words.drop(1).joinToString(" ")
        }
        restAfter = restAfter.replace(Regex("^ה(?=\\s|$)"), "").trim()
        var text = CMDWORDS.replace("$before $restAfter", " ").replace(Regex("\\s+"), " ").trim()
        text = text.replace(Regex("^(?:על|ש)\\s+"), "")
        return Cmd.GeoRemind(text, place ?: return null)
    }
}

/** ממיר מספרים במילים ("עשרים וחמש") לספרות, כדי שזיהוי הדיבור יובן גם בלי ספרות. */
object Num {
    private val W = mapOf(
        "אחד" to 1, "אחת" to 1, "שניים" to 2, "שתיים" to 2, "שני" to 2, "שתי" to 2, "שתים" to 2,
        "שלוש" to 3, "שלושה" to 3, "ארבע" to 4, "ארבעה" to 4, "חמש" to 5, "חמישה" to 5,
        "שש" to 6, "שישה" to 6, "שבע" to 7, "שבעה" to 7, "שמונה" to 8, "תשע" to 9, "תשעה" to 9,
        "עשר" to 10, "עשרה" to 10, "עשרים" to 20, "שלושים" to 30, "ארבעים" to 40,
        "חמישים" to 50, "שישים" to 60, "שבעים" to 70, "שמונים" to 80, "תשעים" to 90
    )

    private fun value(tok: String, inRun: Boolean): Int? {
        W[tok]?.let { return it }
        if (inRun && tok.length > 2 && tok.startsWith("ו")) return W[tok.substring(1)]
        return null
    }

    fun convert(s: String): String {
        val toks = s.split(" ")
        val out = mutableListOf<String>()
        var acc = 0
        var inRun = false
        fun flush() { if (inRun) { out.add(acc.toString()); acc = 0; inRun = false } }
        for (t in toks) {
            val v = value(t, inRun)
            if (v != null) { acc += v; inRun = true } else { flush(); out.add(t) }
        }
        flush()
        return out.joinToString(" ")
    }
}
