package il.ozer.koli

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** זיהוי דיבור בעברית. מעדיף זיהוי מקומי (אופליין) כשחבילת השפה מותקנת. האזנה רק בזמן לחיצה. */
class Speech(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onProblem: (String) -> Unit
) {
    private var rec: SpeechRecognizer? = null

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start() {
        stop()
        if (!available()) { onProblem("זיהוי דיבור לא זמין במכשיר"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) = onState(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                onState(false)
                val msg = when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא שמעתי, נסה שוב"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "אין הרשאת מיקרופון"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "אין חבילת עברית אופליין. הורד אותה בהגדרות של Google > זיהוי דיבור"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "העברית לא זמינה לזיהוי. ראה מסך בדיקת תקלות"
                    else -> "שגיאת זיהוי ($e)"
                }
                onProblem(msg)
            }
            override fun onResults(b: Bundle?) {
                onState(false)
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onProblem("לא שמעתי, נסה שוב") else onText(t)
            }
        })
        rec = r
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        r.startListening(i)
    }

    fun stop() {
        rec?.destroy()
        rec = null
        onState(false)
    }
}

class Tts(ctx: Context) {
    private var tts: TextToSpeech? = null
    var ready = false
    var hebrew = false
    private val main = Handler(Looper.getMainLooper())
    private var done: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) {
                val r = tts?.setLanguage(Locale("he", "IL"))
                hebrew = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) { main.post { done?.invoke(); done = null } }
                    override fun onDone(id: String?) { main.post { done?.invoke(); done = null } }
                })
            }
        }
    }

    fun speak(text: String, then: (() -> Unit)? = null) {
        if (!ready || !hebrew) { then?.invoke(); return }
        done = then
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
