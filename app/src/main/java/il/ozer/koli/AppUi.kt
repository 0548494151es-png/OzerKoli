package il.ozer.koli

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUi(a: MainActivity) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1E3A8A))) {
            var tab by remember { mutableStateOf(0) }
            val teacher = a.state.tick.let { Store.bool(a, "teacher") }
            val tabs = buildList {
                add("עוזר" to Icons.Filled.Mic)
                add("תזכורות" to Icons.Filled.Notifications)
                add("שבת" to Icons.Filled.Timer)
                if (teacher) add("מורה" to Icons.Filled.School)
                add("הגדרות" to Icons.Filled.Settings)
            }
            if (tab >= tabs.size) tab = 0
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(t.second, null) }, label = { Text(t.first) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tabs[tab].first) {
                        "עוזר" -> AssistantScreen(a)
                        "תזכורות" -> RemindersScreen(a)
                        "שבת" -> ShabbatScreen(a)
                        "מורה" -> TeacherScreen(a)
                        else -> SettingsScreen(a)
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantScreen(a: MainActivity) {
    val s = a.state
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(s.log.reversed()) { line ->
                Text(line, Modifier.padding(vertical = 4.dp),
                    color = if (line.startsWith("אתה")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        s.confirm?.let { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.text, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { a.yes() }) { Text("כן") }
                        OutlinedButton(onClick = { a.no() }) { Text("לא") }
                    }
                }
            }
        }
        s.choice?.let { ch ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(ch.text)
                    ch.options.forEachIndexed { i, o ->
                        OutlinedButton(onClick = { a.pick(i) }, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(o) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("או הקלד פקודה") }, singleLine = true)
            IconButton(onClick = { if (typed.isNotBlank()) { a.typed(typed); typed = "" } }) { Icon(Icons.Filled.Send, "שלח") }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            LargeFloatingActionButton(onClick = { if (s.listening) a.stopListening() else a.listen() }) {
                Icon(if (s.listening) Icons.Filled.Stop else Icons.Filled.Mic, "דבר")
            }
        }
        Text(
            if (s.listening) "מקשיב..." else "לחץ ודבר. למשל: תתקשר לאמא, טיימר 10 דקות, תזכיר לי מחר ב-8 לקנות חלב",
            Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodySmall
        )
    }
}
