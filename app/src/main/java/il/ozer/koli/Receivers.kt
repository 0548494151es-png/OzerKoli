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
        val at = i.getLongExtra("at", 0L)
        val late = if (at > 0) (System.currentTimeMillis() - at) / 1000 else 0L
        Elog.add(c, "קפץ: $type \"$title\" (איחור $late שניות)")
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
                val upd = if (next > 0) it.copy(timeMs = next, fired = false) else it.copy(fired = true)
                Store.saveItems(c, list.map { x -> if (x.id == id) upd else x })
                if (next > 0) Scheduler.scheduleItem(c, upd)
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
    override fun onReceive(c: Context, i: Intent) {
        Elog.add(c, "תוזמן מחדש (${i.action?.substringAfterLast('.')})")
        Scheduler.rescheduleAll(c)
    }
}

class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Elog.add(c, "התקבל אירוע גיאו-גדר מהמערכת")
        val ev = GeofencingEvent.fromIntent(i)
        if (ev == null) { Elog.add(c, "גיאו-גדר: האירוע ריק"); return }
        if (ev.hasError()) {
            Elog.add(c, "שגיאת גיאו-גדר: " + com.google.android.gms.location.GeofenceStatusCodes.getStatusCodeString(ev.errorCode))
            return
        }
        val t = ev.geofenceTransition
        if (t != Geofence.GEOFENCE_TRANSITION_ENTER && t != Geofence.GEOFENCE_TRANSITION_EXIT) {
            Elog.add(c, "גיאו-גדר: סוג מעבר לא צפוי ($t)")
            return
        }
        val ids = ev.triggeringGeofences?.map { it.requestId } ?: return
        val geos = Store.geos(c)
        val loc = ev.triggeringLocation
        val places = Store.places(c)
        geos.filter { ids.contains("g${it.id}") }.forEach { g ->
            val p = places.firstOrNull { x -> x.name == g.place }
            var d = -1
            if (loc != null && p != null) {
                val r = FloatArray(1)
                android.location.Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, r)
                d = r[0].toInt()
            }
            Elog.add(c, "גיאו-גדר: ${g.place} " + (if (t == Geofence.GEOFENCE_TRANSITION_EXIT) "יציאה" else "כניסה") +
                ", מרחק מהנקודה השמורה $d מ', דיוק מיקום ${loc?.accuracy?.toInt() ?: -1} מ'")
        }
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
