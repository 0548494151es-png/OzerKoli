package il.ozer.koli

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Contact(val name: String, val number: String)

object Contacts {
    fun granted(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun clean(s: String) = s.lowercase().replace(Regex("""[\"'״׳.\-]"""), "").replace(Regex("\\s+"), " ").trim()

    /** מחזיר התאמות לפי דירוג: זהות מלאה, התחלה, הכלה, כל המילים. */
    fun find(c: Context, query: String): List<Contact> {
        val q = clean(query)
        if (q.isEmpty() || !granted(c)) return emptyList()
        val all = mutableListOf<Contact>()
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val p = it.getString(1) ?: continue
                all.add(Contact(n, p))
            }
        }
        val words = q.split(" ")
        fun score(n: String): Int {
            val x = clean(n)
            return when {
                x == q -> 4
                x.startsWith(q) -> 3
                x.contains(q) -> 2
                words.all { w -> x.contains(w) } -> 1
                else -> 0
            }
        }
        val scored = all.map { it to score(it.name) }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return emptyList()
        return scored.filter { it.second == best }.map { it.first }
            .distinctBy { it.name + it.number.filter { ch -> ch.isDigit() } }
            .take(5)
    }
}
