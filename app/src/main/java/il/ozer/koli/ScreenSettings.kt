package il.ozer.koli

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Check1(val name: String, val ok: Boolean, val hint: String, val fix: (() -> Unit)?)

@Composable
fun SettingsScreen(a: MainActivity) {
    val tick = a.state.tick
    var v by remember { mutableStateOf(0) }
    val teacher = remember(v) { Store.bool(a, "teacher") }
    val speakOn = remember(v) { Store.bool(a, "speak_on") }

    fun granted(p: String) = a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    fun ask(vararg p: String) = a.requestPerms(arrayOf(*p)) { v++ }
    fun appSettings() = a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))

    val checks = remember(tick, v) {
        val (speechOk, ttsReady, ttsHe) = a.diagnosticsInfo()
        val am = a.getSystemService(AlarmManager::class.java)
        buildList {
            add(Check1("מיקרופון", granted(Manifest.permission.RECORD_AUDIO), "נדרש לדיבור") { ask(Manifest.permission.RECORD_AUDIO) })
            add(Check1("שירות זיהוי דיבור", speechOk, "התקן/עדכן את אפליקציית Google", null))
            add(Check1("הקראה בעברית (TTS)", ttsReady && ttsHe, "הגדרות > ניהול כללי > טקסט לדיבור > הורד עברית", null))
            add(Check1("אנשי קשר", granted(Manifest.permission.READ_CONTACTS), "נדרש לחיפוש מי להתקשר") { ask(Manifest.permission.READ_CONTACTS) })
            add(Check1("חיוג", granted(Manifest.permission.CALL_PHONE), "בלי זה ייפתח החייגן ותלחץ חיוג ידנית") { ask(Manifest.permission.CALL_PHONE) })
            add(Check1("התראות", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS), "בלי זה לא יופיעו תזכורות") { ask(Manifest.permission.POST_NOTIFICATIONS) })
            add(Check1("התראות מדויקות", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), "אחרת תזכורות עלולות להתאחר") {
                a.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${a.packageName}")))
            })
            add(Check1("מיקום מדויק", Geo.hasFine(a), "לתזכורות לפי מקום") { ask(Manifest.permission.ACCESS_FINE_LOCATION) })
            add(Check1("מיקום 'תמיד'", Geo.hasBackground(a), "בהגדרות האפליקציה: מיקום > אפשר תמיד") { appSettings() })
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("הגדרות", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("מצב מורה")
                    Text("מערכת שעות עם התראות ורשימות בדיקה", style = MaterialTheme.typography.bodySmall)
                }
                Switch(teacher, {
                    Store.setBool(a, "teacher", it)
                    if (it) Scheduler.scheduleLessons(a)
                    else Store.lessons(a).forEach { l -> Scheduler.cancel(a, l.id, "lesson") }
                    v++; a.state.tick++
                })
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("הקראה בקול של תשובות")
                    Text("כבוי: התשובות מופיעות רק בכתב", style = MaterialTheme.typography.bodySmall)
                }
                Switch(speakOn, { Store.setBool(a, "speak_on", it); v++ })
            }
            Text(
                "הפעלה מהירה בלי לפתוח את האפליקציה: ווידג'ט על מסך הבית, אריח בהגדרות המהירות (החלקה מלמעלה), " +
                    "או לחיצה ארוכה על סמל האפליקציה > \"דבר\". אין צורך להגדיר כעוזר דיגיטלי.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)
            )
            Text("בדיקת תקלות", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(checks) { c ->
            ListItem(
                headlineContent = { Text((if (c.ok) "✓ " else "✗ ") + c.name) },
                supportingContent = { if (!c.ok) Text(c.hint) },
                trailingContent = { if (!c.ok && c.fix != null) TextButton(onClick = { c.fix.invoke() }) { Text("תקן") } }
            )
            HorizontalDivider()
        }
        item {
            Text(
                "זיהוי דיבור אופליין בעברית: הגדרות Google > זיהוי דיבור > שפות > עברית > הורד לשימוש ללא חיבור.",
                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
