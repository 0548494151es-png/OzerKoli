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

/** כותרת פשוטה
