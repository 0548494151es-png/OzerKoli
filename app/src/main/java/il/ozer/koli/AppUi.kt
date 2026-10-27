package il.ozer.koli

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun AppUi(a: MainActivity) {
    val dark = isDarkMode(a.state.themeMode, isSystemInDarkTheme())
    LaunchedEffect(dark) {
        val clear = android.graphics.Color.TRANSPARENT
        a.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
            navigationBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        )
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AppTheme(a.state.themeMode) {
            var tab by remember { mutableStateOf(0) }
            val teacher = a.state.tick.let { Store.bool(a, "teacher") }
            val hasUpdate = a.state.update.status.let { it == "available" || it == "ready" || it == "downloading" }
            val tabs = buildList {
                add("עוזר" to Icons.Filled.Chat)
                add("תזכורות" to Icons.Filled.Notifications)
                add("מקומות" to Icons.Filled.Place)
                add("שבת" to Icons.Filled.Timer)
                if (teacher) add("מורה" to Icons.Filled.School)
                add("הגדרות" to Icons.Filled.Settings)
            }
            if (tab >= tabs.size) tab = 0
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = {
                                    if (t.first == "הגדרות" && hasUpdate) BadgedBox(badge = { Badge() }) { Icon(t.second, null) }
                                    else Icon(t.second, null)
                                },
                                label = { Text(t.first, maxLines = 1) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
                    Crossfade(targetState = tabs[tab].first, label = "tab") { name ->
                        when (name) {
                            "עוזר" -> AssistantScreen(a)
                            "תזכורות" -> RemindersScreen(a)
                            "מקומות" -> PlacesScreen(a)
                            "שבת" -> ShabbatScreen(a)
                            "מורה" -> TeacherScreen(a)
                            else -> SettingsScreen(a)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(line: String) {
    val user = line.startsWith("אתה")
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.Start else Arrangement.End) {
        Surface(
            color = if (user) cs.primary else cs.surfaceContainerHigh,
            contentColor = if (user) cs.onPrimary else cs.onSurface,
            shape = if (user) RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp) else RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) { Text(line.substringAfter(": ", line), Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(a: MainActivity) {
    val s = a.state
    var typed by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    val hasContent = s.log.isNotEmpty() || s.confirm != null || s.choice != null
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (hasContent || s.listening) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (s.listening) "מקשיב..." else "", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                if (hasContent) {
                    TextButton(onClick = { s.log.clear(); s.confirm = null; s.choice = null }) { Text("נקה") }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!hasContent) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("במה אפשר לעזור?", style = MaterialTheme.typography.titleLarge)
                    Text("נסה למשל:", color = cs.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("טיימר 10 דקות", "תזכיר לי מחר ב-8 לקנות חלב", "כמה זמן עד שבת", "מה יש לי מחר").forEach { ex ->
                            SuggestionChip(onClick = { a.typed(ex) }, label = { Text(ex) })
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp), reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { items(s.log.reversed()) { line -> Bubble(line) } }
            }
        }
        s.confirm?.let { c ->
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), container = cs.primaryContainer) {
                Text(c.text, style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { a.yes() }) { Text("כן") }
                    OutlinedButton(onClick = { a.no() }) { Text("לא") }
                }
            }
        }
        s.choice?.let { ch ->
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), container = cs.primaryContainer) {
                Text(ch.text, color = cs.onPrimaryContainer)
                ch.options.forEachIndexed { i, o ->
                    OutlinedButton(onClick = { a.pick(i) }, Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(o) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("הקלד פקודה או לחץ על המיקרופון") },
                singleLine = true, shape = RoundedCornerShape(28.dp)
            )
            FilledIconButton(
                onClick = { if (s.listening) a.stopListening() else a.listen() },
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (s.listening) cs.error else cs.primary)
            ) { Icon(if (s.listening) Icons.Filled.Stop else Icons.Filled.Mic, "דבר", Modifier.size(22.dp)) }
            FilledIconButton(onClick = { if (typed.isNotBlank()) { a.typed(typed); typed = "" } }) {
                Icon(Icons.AutoMirrored.Filled.Send, "שלח")
            }
        }
    }
}
