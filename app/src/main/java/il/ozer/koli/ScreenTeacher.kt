package il.ozer.koli

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private val DAY_NAMES = listOf("א" to 1, "ב" to 2, "ג" to 3, "ד" to 4, "ה" to 5, "ו" to 6)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherScreen(a: MainActivity) {
    var v by remember { mutableStateOf(0) }
    val lessons = remember(v) { Store.lessons(a).sortedWith(compareBy({ it.dow }, { it.hour }, { it.min })) }
    val checks = remember(v) { Store.checks(a) }
    var addLesson by remember { mutableStateOf(false) }
    var addCheck by remember { mutableStateOf(false) }
    var lTitle by remember { mutableStateOf("") }
    var lDay by remember { mutableStateOf(1) }
    var lH by remember { mutableStateOf(8) }
    var lM by remember { mutableStateOf(0) }
    var lBefore by remember { mutableStateOf(5) }
    var cTitle by remember { mutableStateOf("") }
    var cTotal by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { SchoolCard(a) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מערכת שעות", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { addLesson = true }) { Icon(Icons.Filled.Add, null); Text("שיעור") }
            }
        }
        items(lessons) { l ->
            ListItem(
                headlineContent = { Text(l.title) },
                supportingContent = {
                    Text("יום ${DAY_NAMES.first { it.second == l.dow }.first} · " +
                        String.format(Locale.US, "%02d:%02d", l.hour, l.min) + " · התראה ${l.before} דק' לפני")
                },
                trailingContent = {
                    IconButton(onClick = {
                        Scheduler.cancel(a, l.id, "lesson")
                        Store.saveLessons(a, Store.lessons(a).filter { it.id != l.id }); v++
                    }) { Icon(Icons.Filled.Delete, "מחק") }
                }
            )
            HorizontalDivider()
        }
        item {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("בדיקת מבחנים ומשימות", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { addCheck = true }) { Icon(Icons.Filled.Add, null); Text("רשימה") }
            }
        }
        items(checks) { ck ->
            ListItem(
                headlineContent = { Text(ck.title) },
                supportingContent = { LinearProgressIndicator(progress = { if (ck.total == 0) 0f else ck.done.toFloat() / ck.total }, Modifier.fillMaxWidth()) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { upd(a, ck.copy(done = maxOf(0, ck.done - 1))); v++ }) { Text("-") }
                        Text(" ${ck.done}/${ck.total} ")
                        OutlinedButton(onClick = { upd(a, ck.copy(done = minOf(ck.total, ck.done + 1))); v++ }) { Text("+") }
                        IconButton(onClick = { Store.saveChecks(a, Store.checks(a).filter { it.id != ck.id }); v++ }) {
                            Icon(Icons.Filled.Delete, "מחק")
                        }
                    }
                }
            )
            HorizontalDivider()
        }
    }

    if (addLesson) {
        AlertDialog(
            onDismissRequest = { addLesson = false },
            title = { Text("שיעור חדש") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lTitle, { lTitle = it }, label = { Text("שם השיעור / כיתה") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAY_NAMES.forEach { d -> FilterChip(lDay == d.second, { lDay = d.second }, { Text(d.first) }) }
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(a, { _, h, m -> lH = h; lM = m }, lH, lM, true).show()
                    }) { Text("שעה: " + String.format(Locale.US, "%02d:%02d", lH, lM)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("התראה לפני:")
                        OutlinedButton(onClick = { if (lBefore > 0) lBefore -= 5 }) { Text("-") }
                        Text("$lBefore דק'")
                        OutlinedButton(onClick = { if (lBefore < 60) lBefore += 5 }) { Text("+") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (lTitle.isNotBlank()) {
                        val l = Lesson(Store.nextId(a), lTitle, lDay, lH, lM, lBefore)
                        Store.saveLessons(a, Store.lessons(a) + l)
                        Scheduler.scheduleLesson(a, l)
                        lTitle = ""; addLesson = false; v++
                    }
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { addLesson = false }) { Text("ביטול") } }
        )
    }
    if (addCheck) {
        AlertDialog(
            onDismissRequest = { addCheck = false },
            title = { Text("רשימת בדיקה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(cTitle, { cTitle = it }, label = { Text("למשל: מבחן מדעים ו'2") }, singleLine = true)
                    OutlinedTextField(cTotal, { cTotal = it.filter { ch -> ch.isDigit() } }, label = { Text("כמה פריטים לבדוק") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = cTotal.toIntOrNull()
                    if (cTitle.isNotBlank() && n != null && n > 0) {
                        Store.saveChecks(a, Store.checks(a) + Check(Store.nextId(a), cTitle, n, 0))
                        cTitle = ""; cTotal = ""; addCheck = false; v++
                    }
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { addCheck = false }) { Text("ביטול") } }
        )
    }
}

private fun upd(a: MainActivity, ck: Check) =
    Store.saveChecks(a, Store.checks(a).map { if (it.id == ck.id) ck else it })
