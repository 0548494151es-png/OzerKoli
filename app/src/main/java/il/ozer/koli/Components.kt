package il.ozer.koli

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun brandBrush(): Brush {
    val dark = LocalIsDark.current
    return Brush.linearGradient(
        if (dark) listOf(Color(0xFF166534), Color(0xFF0B2A1C)) else listOf(Color(0xFF22A559), Color(0xFF0F5132))
    )
}

/** כותרת פשוטה בראש כל מסך (בלי רקע ירוק ובלי סמלים) */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, icon: ImageVector? = null, trailing: @Composable (RowScope.() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (trailing != null) {
            Surface(color = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer, bg: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { content() }
}
