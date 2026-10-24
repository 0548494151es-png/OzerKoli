package il.ozer.koli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val type = i.getStringExtra("type") ?: return
        val id = i.getIntExtra("id", 0)
        val title = i.getStringExtra("title") ?: ""
        when (type) {
            "item" -> {
                Notif.alert(c, id, "תזכורת", title)
                val list = Store.items(c)
                val it = list.firstOrNull { x -> x.id == id } ?: return
                val next = when (it.repeat) {
                    "daily" -> it.timeMs + 86_400_000L
                    "weekly" -> it.timeMs + 7 * 86_400_000L
                    else -> 0L
                }
                if (next > 0) {
                    val upd = it.copy(timeMs = next)
                    Store.saveItems(c, list.map { x -> if (x.id == id) upd else x })
                    Scheduler.scheduleItem(c, upd)
                }
            }
            "timer" -> {
                Notif.cancel(c, id)
                Notif.alert(c, id, "הטיימר הסתיים", title)
            }
            "lesson" -> {
                Notif.alert(c, id, "שיעור מתקרב", title)
                Store.lessons(c).firstOrNull { x -> x.id == id }?.let { Scheduler.scheduleLesson(c, it) }
            }
            "shabbat_start" -> Shabbat.showCountdown(c)
            "shabbat_end" -> {
                Notif.cancel(c, Shabbat.NOTIF_ID)
                Notif.alert(c, Shabbat.NOTIF_ID + 10, "הגיע זמן הדלקת נרות", "שבת שלום!")
                Shabbat.schedule(c)
            }
            "shabbat_ask" -> Notif.alert(c, Shabbat.NOTIF_ID + 11, "לפני שבת", "כדאי לעדכן את זמן הדלקת הנרות באפליקציה")
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.rescheduleAll(c)
}

class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ev = GeofencingEvent.fromIntent(i) ?: return
        if (ev.hasError()) return
        val t = ev.geofenceTransition
        if (t != Geofence.GEOFENCE_TRANSITION_ENTER && t != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val ids = ev.triggeringGeofences?.map { it.requestId } ?: return
        val geos = Store.geos(c)
        val fired = geos.filter { ids.contains("g${it.id}") && it.on && it.leave == (t == Geofence.GEOFENCE_TRANSITION_EXIT) }
        fired.forEach {
            val head = if (it.leave) "יצאת מ${it.place}" else "הגעת ל${it.place}"
            Notif.alert(c, it.id, head, it.text)
        }
        // תזכורת "פעם אחת" נמחקת אחרי שהופעלה; "בכל פעם" נשארת
        val remove = fired.filter { !it.always }.map { it.id }
        if (remove.isNotEmpty()) {
            Store.saveGeos(c, geos.filter { g -> !remove.contains(g.id) })
            Geo.registerAll(c)
        }
    }
}
