package il.ozer.koli

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** יומן אירועים פנימי (200 שורות אחרונות) לאבחון תזכורות שלא קפצו. נשמר בטלפון בלבד. */
object Elog {
    private val f = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)

    @Synchronized
    fun add(c: Context, text: String) {
        try {
            val sp = c.getSharedPreferences("ozer_log", Context.MODE_PRIVATE)
            val a = try { JSONArray(sp.getString("log", "[]")) } catch (e: Exception) { JSONArray() }
            a.put(f.format(Date()) + "  " + text)
            val out = JSONArray()
            for (i in maxOf(0, a.length() - 200) until a.length()) out.put(a.get(i))
            sp.edit().putString("log", out.toString()).apply()
        } catch (_: Exception) {}
    }

    fun all(c: Context): List<String> {
        val sp = c.getSharedPreferences("ozer_log", Context.MODE_PRIVATE)
        val a = try { JSONArray(sp.getString("log", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getString(it) }.reversed()
    }

    fun clear(c: Context) {
        c.getSharedPreferences("ozer_log", Context.MODE_PRIVATE).edit().remove("log").apply()
    }

    fun fmt(ms: Long): String = f.format(Date(ms))
}
