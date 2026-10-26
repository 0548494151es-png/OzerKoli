package il.ozer.koli

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SchoolCard(a: MainActivity) {
    var v by remember { mutableStateOf(0) }
    val extra = remember(v) { SchoolYear.extraOff(a) }
    val left = remember(v, a.state.tick) { SchoolYear.schoolDaysLeft(a) }
    val cal = remember(v, a.state.tick) { SchoolYear.calendarDaysLeft() }
    val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("עד סוף שנת הלימודים תשפ\"ז", style = MaterialTheme.typography.titleMedium)
            if (cal < 0) {
                Text("שנת הלימודים הסתיימה")
            } else {
                Text("$left ימי לימוד", style = MaterialTheme.typography.headlineMedium)
                Text("ראשון עד חמישי, עד 30 ביוני 2027 · $cal ימים בלוח השנה (כ-${cal / 7} שבועות)",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (extra.isNotEmpty()) Text("ימים נוספים בלי לימודים בבית הספר:", style = MaterialTheme.typography.bodySmall)
            extra.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.format(fmt), Modifier.weight(1f))
                    IconButton(onClick = { SchoolYear.removeOff(a, d); v++ }) { Icon(Icons.Filled.Delete, "מחק") }
                }
            }
            OutlinedButton(onClick = {
                val now = LocalDate.now()
                DatePickerDialog(a, { _, y, m, day -> SchoolYear.addOff(a, LocalDate.of(y, m + 1, day)); v++ },
                    now.year, now.monthValue - 1, now.dayOfMonth).show()
            }) { Text("הוסף יום ללא לימודים") }
            Text(
                "לפי לוח משרד החינוך כפי שפורסם. כולל חנוכה, פורים, פסח, יום העצמאות ושבועות. " +
                    "יום הזיכרון (חצי יום) נספר כיום לימוד. לפני 14:00 היום נכלל בספירה.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
