package il.ozer.koli

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun AppUi(a: MainActivity) {
    val dark = isDarkMode(a.state.themeMode, isSystemInDarkTheme())
    LaunchedEffect(dark) {
        val clear = android.graphics.Color.TRANSPARENT
        a.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(clear),
            navigationBarStyle = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        )
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AppTheme(a.state.themeMode) {
            var tab by remember { mutableStateOf(0) }
            val teacher = a.state.tick.let { Store.bool(a, "teacher") }
            val hasUpdate = a.state.update.status.let { it == "available" || it == "ready" || it == "downloading" }
            val tabs = buildList {
                add("עוזר" to Icons.Filled.Mic)
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

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val scale by inf.animateFloat(1f, 1.6f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val alpha by inf.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(76.dp).scale(scale).alpha(alpha).background(MaterialTheme.colorScheme.primary, CircleShape))
        Box(
            Modifier.size(76.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(brandBrush()).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, "דבר", tint = Color.White, modifier = Modifier.size(38.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(a: MainActivity) {
    val s = a.state
    var typed by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("עוזר קולי", if (s.listening) "מקשיב..." else "לחץ על המיקרופון ודבר", Icons.Filled.Mic) {
            if (s.log.isNotEmpty() || s.confirm != null || s.choice != null)
                TextButton(
                    onClick = { s.log.clear(); s.confirm = null; s.choice = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("נקה") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (s.log.isEmpty() && s.confirm == null && s.choice == null) {
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
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                typed, { typed = it }, Modifier.weight(1f), placeholder = { Text("או הקלד פקודה") },
                singleLine = true, shape = RoundedCornerShape(28.dp)
            )
            FilledIconButton(onClick = { if (typed.isNotBlank()) { a.typed(typed); typed = "" } }) {
                Icon(Icons.AutoMirrored.Filled.Send, "שלח")
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(s.listening) { if (s.listening) a.stopListening() else a.listen() }
        }
    }
}
