package il.ozer.koli

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object Notif {
    const val CH_ALERT = "alerts"
    const val CH_COUNT = "countdown"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "תזכורות והתראות", NotificationManager.IMPORTANCE_HIGH)
        )
        // ערוץ שקט לספירה לאחור, כדי שלא ירעיש
        nm.createNotificationChannel(
            NotificationChannel(CH_COUNT, "ספירה לאחור", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun canPost(c: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun alert(c: Context, id: Int, title: String, text: String) {
        if (!canPost(c)) return
        channels(c)
        val n = NotificationCompat.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open(c))
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    /**
     * ספירה לאחור שהמערכת עצמה מציירת (כרונומטר).
     * האפליקציה לא רצה ולא מתעוררת בזמן הספירה, לכן אין צריכת סוללה.
     */
    fun countdown(c: Context, id: Int, title: String, text: String, targetMs: Long) {
        if (!canPost(c)) return
        channels(c)
        val b = NotificationCompat.Builder(c, CH_COUNT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(targetMs)
            .setContentIntent(open(c))
        val remaining = targetMs - System.currentTimeMillis()
        if (remaining > 0) b.setTimeoutAfter(remaining)
        c.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    fun cancel(c: Context, id: Int) = c.getSystemService(NotificationManager::class.java).cancel(id)
}
