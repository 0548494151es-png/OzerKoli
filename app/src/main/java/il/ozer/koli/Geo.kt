package il.ozer.koli

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.GeofenceStatusCodes
import android.os.Handler
import android.os.Looper
import java.util.Locale
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** גיאו-גדרות של אנדרואיד: המערכת עצמה מנהלת אותן, בלי שירות שרץ ובלי עלות סוללה משמעותית. */
object Geo {
    const val RADIUS = 100f
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
        if (!hasFine(c)) { client.removeGeofences(pi(c)); return }
        val places = Store.places(c)
        val fences = Store.geos(c).filter { it.on }.mapNotNull { g ->
            val p = places.firstOrNull { it.name == g.place } ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId("g${g.id}")
                .setCircularRegion(p.lat, p.lng, RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(if (g.leave) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        // קודם מסירים את הישנות, ורק כשהסתיים מוסיפים: כך אי אפשר שההסרה תמחק את החדשות
        client.removeGeofences(pi(c)).addOnCompleteListener {
            if (fences.isEmpty()) return@addOnCompleteListener
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(fences)
                .build()
            val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS
            Elog.add(c, "מצב: הרשאת 'תמיד' " + (if (hasBackground(c)) "כן" else "לא") +
                ", מיקום במכשיר " + (if (LocationManagerCompat.isLocationEnabled(lm)) "פועל" else "כבוי") +
                ", Google Play services " + (if (gms) "זמין" else "לא זמין"))
            client.addGeofences(req, pi(c))
                .addOnSuccessListener { Elog.add(c, "נרשמו ${fences.size} גדרות מיקום (רדיוס ${RADIUS.toInt()} מ')") }
                .addOnFailureListener { e ->
                    val code = (e as? ApiException)?.statusCode
                    Elog.add(c, "רישום גדרות מיקום נכשל: " + (if (code != null) GeofenceStatusCodes.getStatusCodeString(code) else e.message))
                }
        }
    }

    /** שומר את המיקום הנוכחי בשם נתון. הבקשה חד-פעמית. */
    @SuppressLint("MissingPermission")
    fun saveCurrentPlace(c: Context, name: String, done: (Boolean) -> Unit) {
        if (!hasFine(c)) { done(false); return }
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(20000)
            .setMaxUpdateAgeMillis(10000)
            .build()
        LocationServices.getFusedLocationProviderClient(c)
            .getCurrentLocation(req, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) { done(false); return@addOnSuccessListener }
                val list = Store.places(c).filter { it.name != name }.toMutableList()
                list.add(Place(name, loc.latitude, loc.longitude))
                Store.savePlaces(c, list)
                Elog.add(c, "נשמר מקום \"$name\", דיוק ${loc.accuracy.toInt()} מ'")
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

    /** בדיקת מיקום: איפה אני עכשיו וכמה רחוק מכל מקום שמור. */
    @SuppressLint("MissingPermission")
    fun checkNow(c: Context, cb: (List<String>) -> Unit) {
        if (!hasFine(c)) { cb(listOf("אין הרשאת מיקום")); return }
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(20000)
            .setMaxUpdateAgeMillis(10000)
            .build()
        LocationServices.getFusedLocationProviderClient(c)
            .getCurrentLocation(req, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) { cb(listOf("לא התקבל מיקום")); return@addOnSuccessListener }
                val out = mutableListOf("המיקום שלך: דיוק ${loc.accuracy.toInt()} מ'")
                Store.places(c).forEach { p ->
                    val r = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, r)
                    val d = r[0].toInt()
                    out.add("${p.name}: ${d} מ' " + if (d <= RADIUS) "(בתוך הרדיוס)" else "(מחוץ לרדיוס)")
                }
                Elog.add(c, "בדיקת מיקום: " + out.joinToString(" | "))
                cb(out)
            }
            .addOnFailureListener { cb(listOf("לא הצלחתי לקבל מיקום")) }
    }
}
