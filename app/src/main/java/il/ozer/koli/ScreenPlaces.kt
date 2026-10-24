package il.ozer.koli

import android.Manifest
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen(a: MainActivity) {
    var v by remember { mutableStateOf(0) }
    val geos = remember(v, a.state.tick) { Store.geos(a) }
    val places = remember(v, a.state.tick) { Store.places(a) }
    var showAdd by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var pickPlace by remember { mutableStateOf("") }
    var leave by remember { mutableStateOf(false) }
    var always by remember { mutableStateOf(false) }
    var pName by remember { mutableStateOf("") }
    var pAddr by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Triple<String, Double, Double>>?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("תזכורות לפי מקום", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { showAdd = true }, enabled = places.isNotEmpty()) { Icon(Icons.Filled.Add, null); Text("הוסף") }
            }
            if (places.isEmpty()) Text("קודם הוסף מקום למטה, ואז אפשר ליצור תזכורת.", style = MaterialTheme.typography.bodySmall)
            if (!Geo.hasBackground(a))
                Text("שים לב: כדי שזה יעבוד כשהאפליקציה סגורה, אשר מיקום 'תמיד' בהגדרות > בדיקת תקלות.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        items(geos) { g ->
            ListItem(
                headlineContent = { Text(g.text) },
                supportingContent = {
                    Text((if (g.leave) "כשאצא מ" else "כשאגיע ל") + g.place + " · " + (if (g.always) "בכל פעם" else "פעם אחת"))
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(g.on, { on ->
                            Store.saveGeos(a, Store.geos(a).map { if (it.id == g.id) it.copy(on = on) else it })
                            Geo.registerAll(a); v++
                        })
                        IconButton(onClick = {
                            Store.saveGeos(a, Store.geos(a).filter { it.id != g.id }); Geo.registerAll(a); v++
                        }) { Icon(Icons.Filled.Delete, "מחק") }
                    }
                }
            )
            HorizontalDivider()
        }
        item {
            Text("המקומות שלי", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
        }
        items(places) { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                trailingContent = {
                    IconButton(onClick = {
                        Store.savePlaces(a, Store.places(a).filter { it.name != p.name })
                        Store.saveGeos(a, Store.geos(a).filter { it.place != p.name })
                        Geo.registerAll(a); v++
                    }) { Icon(Icons.Filled.Delete, "מחק") }
                }
            )
            HorizontalDivider()
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("הוספת מקום", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(pName, { pName = it }, label = { Text("שם (בית, סופר, בית ספר...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pAddr, { pAddr = it }, label = { Text("כתובת (לא חובה)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (pName.isBlank()) { status = "כתוב קודם שם למקום" }
                        else if (!Geo.hasFine(a)) a.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { v++ }
                        else {
                            status = "שומר..."
                            val n = pName.trim()
                            Geo.saveCurrentPlace(a, n) { ok ->
                                status = if (ok) "נשמר: $n" else "לא הצלחתי לקבל מיקום"
                                if (ok) pName = ""
                                v++
                            }
                        }
                    }) { Text("שמור כאן") }
                    OutlinedButton(onClick = {
                        if (pName.isBlank() || pAddr.isBlank()) status = "כתוב שם וכתובת"
                        else {
                            status = "מחפש..."
                            Geo.search(a, pAddr.trim()) { r ->
                                if (r.isEmpty()) status = "לא נמצא. נסה כתובת מלאה יותר (דורש אינטרנט)"
                                else { status = ""; results = r }
                            }
                        }
                    }) { Text("חפש ושמור") }
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    results?.let { r ->
        AlertDialog(
            onDismissRequest = { results = null },
            title = { Text("לחץ על המקום הנכון") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.forEach { t ->
                        OutlinedButton(onClick = {
                            val n = pName.trim()
                            Store.savePlaces(a, Store.places(a).filter { it.name != n } + Place(n, t.second, t.third))
                            Geo.registerAll(a)
                            status = "נשמר: $n"; pName = ""; pAddr = ""; results = null; v++
                        }, Modifier.fillMaxWidth()) { Text(t.first) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { results = null }) { Text("ביטול") } }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("תזכורת מקום חדשה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("מה להזכיר") }, singleLine = true)
                    Text("מקום:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        places.forEach { p -> FilterChip(pickPlace == p.name, { pickPlace = p.name }, { Text(p.name) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!leave, { leave = false }, { Text("כשאגיע") })
                        FilterChip(leave, { leave = true }, { Text("כשאצא") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(always, { always = it }); Text("בכל פעם (ולא רק פעם אחת)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank() && pickPlace.isNotBlank()) {
                        Store.saveGeos(a, Store.geos(a) + GeoRem(Store.nextId(a), text.trim(), pickPlace, leave, always))
                        if (!Geo.hasFine(a)) a.requestPerms(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { Geo.registerAll(a); v++ }
                        else Geo.registerAll(a)
                        text = ""; showAdd = false; v++
                    }
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("ביטול") } }
        )
    }
}
