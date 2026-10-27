package il.ozer.koli

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun pickDateTime(c: Context, cb: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(c, { _, y, m, d ->
        TimePickerDialog(c, { _, h, min ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, h, min, 0); cal.set(Calendar.MILLISECOND, 0)
            cb(cal.timeInMillis)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun RemindersScreen(a: MainActivity) {
    val tick = a.state.tick
    var local by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var isEvent by remember { mutableStateOf(false) }
    val list = remember(tick, local) { Store.items(a).sortedBy { it.timeMs } }
    val f = remember { SimpleDateFormat("EEEE dd/MM HH:mm", Locale("he", "IL")) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("תזכורות ויומן", "${list.size} פריטים", Icons.Filled.Notifications) {
            FilledTonalButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null); Text("הוסף") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            items(list) { it ->
                val past = it.timeMs < System.currentTimeMillis()
                val missed = past && !it.fired && it.repeat == "none" && it.timeMs < System.currentTimeMillis() - 60_000
                ListCard { ListItem(
                    colors = ListItemColors(),
                    leadingContent = { IconBadge(if (it.kind == "event") Icons.Filled.Event else Icons.Filled.Notifications) },
                    headlineContent = { Text(it.title, color = if (missed) MaterialTheme.colorScheme.error else Color.Unspecified) },
                    supportingContent = {
                        Text(
                            (if (it.kind == "event") "אירוע · " else "תזכורת · ") + f.format(Date(it.timeMs)) +
                                (if (missed) " (הוחמצה: לא קפצה)" else if (past) " (עבר)" else ""),
                            color = if (missed) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            Scheduler.cancelItem(a, it.id)
                            Store.saveItems(a, Store.items(a).filter { x -> x.id != it.id })
                            local++
                        }) { Icon(Icons.Filled.Delete, "מחק") }
                    }
                )
                }
            }
        }
        Text("תזכורות לפי מקום נמצאות בלשונית \"מקומות\".", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("חדש") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("מה") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(isEvent, { isEvent = it }); Text("אירוע ביומן (במקום תזכורת)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        val t = title; val ev = isEvent
                        showAdd = false; title = ""
                        pickDateTime(a) { ms ->
                            val item = Item(Store.nextId(a), t, ms, if (ev) "event" else "reminder", "none")
                            Store.saveItems(a, Store.items(a) + item)
                            Scheduler.scheduleItem(a, item)
                            local++
                        }
                    }
                }) { Text("בחר מועד") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("ביטול") } }
        )
    }
}
