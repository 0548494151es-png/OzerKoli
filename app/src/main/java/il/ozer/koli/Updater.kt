package il.ozer.koli

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateState {
    var status by mutableStateOf("idle") // idle / checking / uptodate / available / downloading / ready / error
    var message by mutableStateOf("")
    var progress by mutableStateOf(0f)
    var info by mutableStateOf<Updater.Info?>(null)
}

/** עדכון עצמי: בודק את ה-Release האחרון ב-GitHub, מוריד APK ופותח התקנה. לא צריך Google Play. */
object Updater {
    const val REPO = "0548494151es-png/OzerKoli"
    class Info(val version: Int, val name: String, val notes: String, val url: String)

    private val main = Handler(Looper.getMainLooper())

    fun installedCode(c: Context): Long =
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0))

    fun installedName(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""

    private fun apkFile(c: Context) = File(File(c.cacheDir, "updates"), "OzerKoli.apk")

    fun cleanup(c: Context) { File(c.cacheDir, "updates").deleteRecursively() }

    /** בדיקה שקטה פעם ב-6 שעות בפתיחת האפליקציה */
    fun autoCheck(a: MainActivity) {
        val last = Store.str(a, "upd_last", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < 6 * 3600_000L) return
        Store.setStr(a, "upd_last", System.currentTimeMillis().toString())
        check(a, true)
    }

    fun check(a: MainActivity, silent: Boolean = false) {
        val u = a.state.update
        if (u.status == "checking" || u.status == "downloading") return
        if (!silent) { u.status = "checking"; u.message = "בודק עדכונים..." }
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "OzerKoli")
                if (conn.responseCode != 200) {
                    main.post { if (!silent) { u.status = "uptodate"; u.message = "עדיין לא פורסמה גרסה לעדכון" } }
                } else {
                    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val ver = Regex("\\d+").find(j.optString("tag_name"))?.value?.toIntOrNull() ?: 0
                    var apk: String? = null
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val s = assets.getJSONObject(i)
                        if (s.optString("name").endsWith(".apk")) { apk = s.optString("browser_download_url"); break }
                    }
                    val name = j.optString("name").ifBlank { "1.$ver" }
                    val notes = j.optString("body")
                    main.post {
                        if (apk != null && ver > installedCode(a)) {
                            u.info = Info(ver, name, notes, apk); u.status = "available"; u.message = ""
                        } else if (!silent) { u.status = "uptodate"; u.message = "האפליקציה מעודכנת" }
                    }
                }
            } catch (e: Exception) {
                main.post { if (!silent) { u.status = "error"; u.message = "לא הצלחתי לבדוק. בדוק חיבור לאינטרנט" } }
            }
        }.start()
    }

    fun download(a: MainActivity) {
        val u = a.state.update
        val info = u.info ?: return
        if (u.status == "downloading") return
        u.status = "downloading"; u.progress = 0f; u.message = "מוריד..."
        Thread {
            try {
                val f = apkFile(a)
                f.parentFile?.mkdirs()
                if (f.exists()) f.delete()
                val conn = URL(info.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                val total = conn.contentLengthLong
                conn.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        var read = 0L; var last = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n); read += n
                            if (total > 0 && read - last > 150_000) { last = read; val p = read.toFloat() / total; main.post { u.progress = p } }
                        }
                    }
                }
                main.post { u.progress = 1f; u.status = "ready"; u.message = "ההורדה הסתיימה"; install(a) }
            } catch (e: Exception) {
                main.post { u.status = "available"; u.message = "ההורדה נכשלה. נסה שוב" }
            }
        }.start()
    }

    fun install(a: MainActivity) {
        val u = a.state.update
        val f = apkFile(a)
        if (!f.exists()) { u.status = "available"; u.message = "הקובץ לא נמצא, הורד שוב"; return }
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            u.message = "אשר לאפליקציה להתקין עדכונים, ואז חזור ולחץ \"התקן עכשיו\""
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.fileprovider", f)
        a.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
