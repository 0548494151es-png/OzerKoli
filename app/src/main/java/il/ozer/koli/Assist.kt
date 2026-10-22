package il.ozer.koli

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.widget.RemoteViews

private fun listenIntent(c: Context) =
    Intent(c, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("listen", true)

/** מאפשר לבחור את האפליקציה כ"עוזר דיגיטלי": לחיצה ארוכה על כפתור ההפעלה פותחת האזנה. לא מאזין ברקע. */
class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            startAssistantActivity(listenIntent(context))
            hide()
        }
    }
}

/** ווידג'ט מסך הבית: לחיצה אחת מתחילה האזנה. */
class MicWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(c, 1, listenIntent(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val rv = RemoteViews(c.packageName, R.layout.widget_mic)
        rv.setOnClickPendingIntent(R.id.widget_root, pi)
        ids.forEach { m.updateAppWidget(it, rv) }
    }
}

/** אריח בהגדרות המהירות (החלקה מלמעלה). */
class MicTile : TileService() {
    override fun onStartListening() {
        qsTile?.let { it.state = android.service.quicksettings.Tile.STATE_INACTIVE; it.updateTile() }
    }

    override fun onClick() {
        val i = listenIntent(this)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
