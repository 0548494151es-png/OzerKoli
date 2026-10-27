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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class Check1(val name: String, val ok: Boolean, val hint: String, val fix: (() -> Unit)?)

@Composable
fun SettingsScreen(a: MainActivity) {
    val tick = a.state.tick
    var v by remember { mutableStateOf(0) }
    val teacher = remember(v) { Store.bool(a, "teacher") }
    var showLog by remember { mutableStateOf(false) }
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
            val pmgr = a.packageManager
            add(Check1("שעון מעורר של המערכת",
                Intent(android.provider.AlarmClock.ACTION_SET_ALARM).resolveActivity(pmgr) != null,
                "אפליקציית השעון לא מגיבה לבקשות. קבע שעון ידנית", null))
            add(Check1("טיימר של המערכת",
                Intent(android.provider.AlarmClock.ACTION_SET_TIMER).resolveActivity(pmgr) != null,
                "אפליקציית השעון לא מגיבה. ייעשה שימוש בטיימר פנימי", null))
            add(Check1("התראות", Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS), "בלי זה לא יופיעו תזכורות") { ask(Manifest.permission.POST_NOTIFICATIONS) })
            add(Check1("התראות מדויקות", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), "אחרת תזכורות עלולות להתאחר") {
                a.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${a.packageName}")))
            })
            add(Check1("מיקום מדויק", Geo.hasFine(a), "לתזכורות לפי מקום") { ask(Manifest.permission.ACCESS_FINE_LOCATION) })
            add(Check1("מיקום 'תמיד'", Geo.hasBackground(a), "בהגדרות האפליקציה: מיקום > אפשר תמיד") { appSettings() })
        }
    }

    if (showLog) LogDialog(a) { showLog = false }

    Column(Modifier.fillMaxSize()) {
    ScreenHeader("הגדרות", "מראה, עדכונים ובדיקת תקלות", Icons.Filled.SettingsIcon)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ThemeCard(a) }
        item { UpdateCard(a) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("הקראה בקול של תשובות")
                        Text("כבוי: התשובות מופיעות רק בכתב", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(speakOn, { Store.setBool(a, "speak_on", it); v++ })
                }
            }
        }
        item { OutlinedButton(onClick = { showLog = true }, Modifier.fillMaxWidth()) { Text("יומן אירועים (למה תזכורת לא קפצה)") } }
        item {
            Text(
                "הפעלה מהירה בלי לפתוח את האפליקציה: ווידג'ט על מסך הבית, אריח בהגדרות המהירות (החלקה מלמעלה), " +
                    "או לחיצה ארוכה על סמל האפליקציה > \"דבר\". אין צורך להגדיר כעוזר דיגיטלי.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            SectionTitle("בדיקת תקלות", Modifier.padding(top = 4.dp))
            AppCard {
                checks.forEachIndexed { idx, c ->
                    if (idx > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (c.ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                            tint = if (c.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text(c.name)
                            if (!c.ok) Text(c.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!c.ok && c.fix != null) TextButton(onClick = { c.fix.invoke() }) { Text("תקן") }
                    }
                }
            }
        }
        item {
            Text(
                "זיהוי דיבור אופליין בעברית: הגדרות Google > זיהוי דיבור > שפות > עברית > הורד לשימוש ללא חיבור.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    }
}

@Composable
private fun ThemeCard(a: MainActivity) {
    AppCard {
        Text("מראה", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("system", "אוטומטי", Icons.Filled.SettingsBrightness),
                Triple("light", "בהיר", Icons.Filled.LightMode),
                Triple("dark", "כהה", Icons.Filled.DarkMode)
            ).forEach { (k, label, ic) ->
                FilterChip(
                    selected = a.state.themeMode == k,
                    onClick = { a.state.themeMode = k; Store.setStr(a, "theme", k) },
                    label = { Text(label) },
                    leadingIcon = { Icon(ic, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(a: MainActivity) {
    val u = a.state.update
    val cur = remember { Updater.installedName(a) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.SystemUpdate)
            Column(Modifier.weight(1f)) {
                Text("עדכון האפליקציה", style = MaterialTheme.typography.titleMedium)
                Text("גרסה מותקנת: $cur", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (u.message.isNotEmpty()) Text(u.message, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
        when (u.status) {
            "checking" -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            "downloading" -> LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            "available" -> {
                Text("גרסה חדשה זמינה: ${u.info?.name ?: ""}", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
                val notes = u.info?.notes ?: ""
                if (notes.isNotBlank()) Text(notes.take(300), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { Updater.download(a) }, Modifier.padding(top = 10.dp)) { Text("הורד והתקן") }
            }
            "ready" -> Button(onClick = { Updater.install(a) }, Modifier.padding(top = 10.dp)) { Text("התקן עכשיו") }
            else -> OutlinedButton(onClick = { Updater.check(a) }, Modifier.padding(top = 10.dp)) { Text("בדוק אם יש עדכון") }
        }
    }
}

@Composable
private fun LogDialog(a: MainActivity, onClose: () -> Unit) {
    var n by remember { mutableStateOf(0) }
    val lines = remember(n) { Elog.all(a) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("יומן אירועים") },
        text = {
            if (lines.isEmpty()) Text("אין אירועים עדיין.")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(lines) { l -> Text(l, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val cm = a.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", lines.reversed().joinToString("\n")))
                }) { Text("העתק") }
                TextButton(onClick = { Elog.clear(a); n++ }) { Text("נקה") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("סגור") } }
    )
}
