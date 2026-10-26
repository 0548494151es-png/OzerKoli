package il.ozer.koli

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(val id: Int, val title: String, val timeMs: Long, val kind: String, val repeat: String, val fired: Boolean = false)
data class Place(val name: String, val lat: Double, val lng: Double)
data class GeoRem(val id: Int, val text: String, val place: String, val leave: Boolean = false, val always: Boolean = false, val on: Boolean = true)
data class Lesson(val id: Int, val title: String, val dow: Int, val hour: Int, val min: Int, val before: Int)
data class Check(val id: Int, val title: String, val total: Int, val done: Int)

/** כל הנתונים נשמרים מקומית במכשיר, בלי אינטרנט. */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("ozer", Context.MODE_PRIVATE)

    private fun <T> load(c: Context, key: String, f: (JSONObject) -> T): MutableList<T> {
        val out = mutableListOf<T>()
        try {
            val a = JSONArray(sp(c).getString(key, "[]"))
            for (i in 0 until a.length()) out.add(f(a.getJSONObject(i)))
        } catch (_: Exception) {}
        return out
    }

    private fun <T> save(c: Context, key: String, list: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray()
        list.forEach { a.put(f(it)) }
        sp(c).edit().putString(key, a.toString()).apply()
    }

    fun nextId(c: Context): Int {
        val n = sp(c).getInt("nextId", 100) + 1
        sp(c).edit().putInt("nextId", n).apply()
        return n
    }

    // ---- תזכורות ואירועי יומן ----
    fun items(c: Context) = load(c, "items") {
        Item(it.getInt("id"), it.getString("t"), it.getLong("ms"), it.getString("k"), it.optString("r", "none"), it.optBoolean("fd", false))
    }
    fun saveItems(c: Context, l: List<Item>) = save(c, "items", l) {
        JSONObject().put("id", it.id).put("t", it.title).put("ms", it.timeMs).put("k", it.kind).put("r", it.repeat).put("fd", it.fired)
    }

    // ---- מקומות ותזכורות מיקום ----
    fun places(c: Context) = load(c, "places") { Place(it.getString("n"), it.getDouble("la"), it.getDouble("lo")) }
    fun savePlaces(c: Context, l: List<Place>) = save(c, "places", l) {
        JSONObject().put("n", it.name).put("la", it.lat).put("lo", it.lng)
    }
    fun geos(c: Context) = load(c, "geos") {
        GeoRem(it.getInt("id"), it.getString("t"), it.getString("p"), it.optBoolean("lv", false), it.optBoolean("al", false), it.optBoolean("on", true))
    }
    fun saveGeos(c: Context, l: List<GeoRem>) = save(c, "geos", l) {
        JSONObject().put("id", it.id).put("t", it.text).put("p", it.place).put("lv", it.leave).put("al", it.always).put("on", it.on)
    }

    // ---- מצב מורה ----
    fun lessons(c: Context) = load(c, "lessons") {
        Lesson(it.getInt("id"), it.getString("t"), it.getInt("d"), it.getInt("h"), it.getInt("m"), it.getInt("b"))
    }
    fun saveLessons(c: Context, l: List<Lesson>) = save(c, "lessons", l) {
        JSONObject().put("id", it.id).put("t", it.title).put("d", it.dow).put("h", it.hour).put("m", it.min).put("b", it.before)
    }
    fun checks(c: Context) = load(c, "checks") {
        Check(it.getInt("id"), it.getString("t"), it.getInt("n"), it.getInt("d"))
    }
    fun saveChecks(c: Context, l: List<Check>) = save(c, "checks", l) {
        JSONObject().put("id", it.id).put("t", it.title).put("n", it.total).put("d", it.done)
    }

    // ---- הגדרות ----
    fun bool(c: Context, k: String, d: Boolean = false) = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun str(c: Context, k: String, d: String = "") = sp(c).getString(k, d) ?: d
    fun setStr(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
}
