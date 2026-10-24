package il.ozer.koli

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.util.Locale
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** גיאו-גדרות של אנדרואיד: המערכת עצמה מנהלת אותן, בלי שירות שרץ ובלי עלות סוללה משמעותית. */
object Geo {
    private fun pi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 7001, Intent(c, GeoReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    fun hasFine(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun registerAll(c: Context) {
        val client = LocationServices.getGeofencingClient(c)
        client.removeGeofences(pi(c))
        if (!hasFine(c)) return
        val places = Store.places(c)
        val fences = Store.geos(c).filter { it.on }.mapNotNull { g ->
            val p = places.firstOrNull { it.name == g.place } ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId("g${g.id}")
                .setCircularRegion(p.lat, p.lng, 150f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(if (g.leave) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        if (fences.isEmpty()) return
        val req = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(fences)
            .build()
        client.addGeofences(req, pi(c))
    }

    /** שומר את המיקום הנוכחי בשם נתון. הבקשה חד-פעמית. */
    @SuppressLint("MissingPermission")
    fun saveCurrentPlace(c: Context, name: String, done: (Boolean) -> Unit) {
        if (!hasFine(c)) { done(false); return }
        LocationServices.getFusedLocationProviderClient(c)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) { done(false); return@addOnSuccessListener }
                val list = Store.places(c).filter { it.name != name }.toMutableList()
                list.add(Place(name, loc.latitude, loc.longitude))
                Store.savePlaces(c, list)
                registerAll(c)
                done(true)
            }
            .addOnFailureListener { done(false) }
    }

    /** חיפוש כתובת או שם מקום (דורש אינטרנט). התוצאה חוזרת בשרשור הראשי. */
    fun search(c: Context, query: String, cb: (List<Triple<String, Double, Double>>) -> Unit) {
        val h = Handler(Looper.getMainLooper())
        Thread {
            val out = try {
                @Suppress("DEPRECATION")
                android.location.Geocoder(c, Locale("he", "IL")).getFromLocationName(query, 4)
                    ?.map { Triple(it.getAddressLine(0) ?: query, it.latitude, it.longitude) } ?: emptyList()
            } catch (e: Exception) { emptyList() }
            h.post { cb(out) }
        }.start()
    }
}
