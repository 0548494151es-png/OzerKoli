package il.ozer.koli

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class Choice(val text: String, val options: List<String>, val onPick: (Int) -> Unit)
class Confirm(val text: String, val onYes: () -> Unit)

class AppState {
    val log = mutableStateListOf<String>()
    var listening by mutableStateOf(false)
    var confirm by mutableStateOf<Confirm?>(null)
    var choice by mutableStateOf<Choice?>(null)
    var tick by mutableStateOf(0) // מרענן רשימות אחרי שינוי
    var themeMode by mutableStateOf("system") // system / light / dark
    val update = UpdateState()
}

class MainActivity : ComponentActivity(), Ui {
    val state = AppState()
    private lateinit var speech: Speech
    private lateinit var tts: Tts
    private lateinit var brain: Brain
    private var permThen: (() -> Unit)? = null
    private var retryConfirmed = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val t = permThen; permThen = null; t?.invoke(); state.tick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        state.themeMode = Store.str(this, "theme", "system")
        Updater.cleanup(this)
        Updater.autoCheck(this)
        Notif.channels(this)
        tts = Tts(this)
        brain = Brain(this, this)
        speech = Speech(this,
            onText = { onSpeech(it) },
            onState = { state.listening = it },
            onProblem = { say(it) })
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        setContent { AppUi(this) }
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunch(intent)
    }

    private fun handleLaunch(i: Intent?) {
        val a = i?.action
        if (a == Intent.ACTION_ASSIST || a == Intent.ACTION_VOICE_COMMAND || i?.getBooleanExtra("listen", false) == true)
            window.decorView.post { listen() }
    }

    override fun onResume() { super.onResume(); state.tick++ }
    override fun onDestroy() { speech.stop(); tts.shutdown(); super.onDestroy() }

    // ---------- האזנה ----------
    fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPerms(arrayOf(Manifest.permission.RECORD_AUDIO)) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
            }
            return
        }
        speech.start()
    }

    fun stopListening() = speech.stop()

    fun diagnosticsInfo(): Triple<Boolean, Boolean, Boolean> = Triple(speech.available(), tts.ready, tts.hebrew)

    private fun onSpeech(text: String) {
        state.log.add("אתה: $text")
        val cf = state.confirm
        if (cf != null) {
            val s = Parser.normalize(text)
            val yes = Regex("^(?:כן|בטח|אשר|אישור|יאללה|אוקיי|אוקי|בסדר|תתקשר|כן תתקשר)").containsMatchIn(s)
            val no = Regex("^(?:לא|בטל|ביטול|עזוב|עצור|די)").containsMatchIn(s)
            when {
                yes -> { state.confirm = null; retryConfirmed = false; cf.onYes() }
                no -> { state.confirm = null; retryConfirmed = false; say("בסדר, ביטלתי.") }
                !retryConfirmed -> { retryConfirmed = true; say("לא הבנתי. כן או לא?") { listen() } }
                else -> { state.confirm = null; retryConfirmed = false; say("ביטלתי.") }
            }
            return
        }
        val ch = state.choice
        if (ch != null) {
            val n = pickIndex(text, ch.options.size)
            if (n != null) { state.choice = null; ch.onPick(n); return }
            state.choice = null
        }
        brain.handle(text)
        state.tick++
    }

    private fun pickIndex(text: String, size: Int): Int? {
        val words = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי")
        words.forEachIndexed { i, w -> if (i < size && text.contains(w)) return i }
        val d = Regex("\\d").find(text)?.value?.toInt()
        if (d != null && d in 1..size) return d - 1
        return null
    }

    // ---------- Ui ----------
    private fun say(text: String, then: (() -> Unit)?) {
        runOnUiThread { state.log.add("עוזר: $text") }
        if (Store.bool(this, "speak_on")) tts.speak(text, then) else then?.invoke()
    }

    override fun say(text: String) = say(text, null)

    override fun confirm(text: String, onYes: () -> Unit) {
        runOnUiThread { state.confirm = Confirm(text, onYes) }
        say("$text אמור כן או לא", { listen() })
    }

    override fun choose(text: String, options: List<String>, onPick: (Int) -> Unit) {
        runOnUiThread { state.choice = Choice(text, options, onPick) }
        say(text, null)
    }

    override fun requestPerms(perms: Array<String>, then: () -> Unit) {
        permThen = then
        permLauncher.launch(perms)
    }

    // כפתורי כן/לא/בחירה במסך
    fun yes() { val c = state.confirm ?: return; state.confirm = null; c.onYes() }
    fun no() { state.confirm = null; say("ביטלתי.") }
    fun pick(i: Int) { val c = state.choice ?: return; state.choice = null; c.onPick(i) }
    fun typed(text: String) { state.log.add("אתה: $text"); brain.handle(text); state.tick++ }
}
