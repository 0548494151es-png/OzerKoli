package il.ozer.koli

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShabbatScreen(a: MainActivity) {
    var v by remember { mutableStateOf(0) }
    val on = remember(v) { Shabbat.enabled(a) }
    val time = remember(v) { Shabbat.timeText(a) }
    val before = remember(v) { Shabbat.hoursBefore(a) }
    val next = remember(v) { Shabbat.describe(a) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("טיימר ערב שבת", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ספירה לאחור שבועית בימי שישי", Modifier.weight(1f))
            Switch(on, { Store.setBool(a, "shabbat_on", it); Shabbat.schedule(a); v++ })
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("זמן הדלקת נרות: $time", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = {
                    TimePickerDialog(a, { _, h, m ->
                        Store.setInt(a, "candle_h", h); Store.setInt(a, "candle_m", m)
                        Shabbat.schedule(a); v++
                    }, Shabbat.hour(a), Shabbat.minute(a), true).show()
                }) { Text("שנה זמן הדלקה") }
                Text("הדלקה הקרובה: $next", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("להתחיל ספירה:")
            OutlinedButton(onClick = { if (before > 1) { Store.setInt(a, "shabbat_before", before - 1); Shabbat.schedule(a); v++ } }) { Text("-") }
            Text("$before שעות לפני")
            OutlinedButton(onClick = { if (before < 12) { Store.setInt(a, "shabbat_before", before + 1); Shabbat.schedule(a); v++ } }) { Text("+") }
        }
        Button(onClick = { Shabbat.showCountdown(a); v++ }) { Text("הצג ספירה עכשיו") }
        Text(
            "ההתראה מציגה ספירה יורדת שהמערכת עצמה מציירת, בלי שהאפליקציה רצה ברקע, לכן אין צריכת סוללה. " +
                "ביום חמישי בערב תקבל תזכורת לעדכן את זמן ההדלקה לשבת, כי הזמן משתנה משבוע לשבוע. " +
                "אפשר גם בקול: \"הדלקת נרות ב-17:32\".",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
