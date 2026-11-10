package org.idktech.lumen.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "Lumen.Assistant"
private const val KATNISS = "com.google.android.katniss"

/** What the assistant decided to do with what it heard. */
sealed class Action {
    data class Say(val text: String) : Action()
    data class OpenApp(val app: TvApp) : Action()
    data class SwitchInput(val input: TvInput) : Action()
    data class ShowTitle(val title: Title) : Action()
    data class SearchTv(val query: String) : Action()
    /** Not a launcher command: the words go to Lumen's Search tab. */
    data class AskGoogle(val query: String) : Action()
    data object Close : Action()
}

/**
 * The voice side of Lumen: Google's on-device recognizer for ears, Google TTS for a voice, a
 * small command grammar for the launcher itself (apps, inputs, titles, weather, time, volume).
 * Anything else is handed to Google Assistant, which is what this TV ships with; Lumen does
 * not pretend to be a chatbot. Phases drive the voice screen.
 */
class Assistant(private val ctx: Context) {
    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    var phase by mutableStateOf(Phase.IDLE)
    var heard by mutableStateOf("")
    var reply by mutableStateOf("")
    var level by mutableFloatStateOf(0f)     // microphone level 0..1

    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String) -> Unit)? = null
    /** Set by the screen: starts Google's own voice-input activity with this intent (its result comes back through `externalResult`). */
    var captureWithActivity: ((Intent) -> Boolean)? = null
    private var triedActivity = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(ctx) { status -> ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) tts?.language = Locale("en", "IN") }
    }

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Must be called on the main thread. */
    fun listen(onFinal: (String) -> Unit) {
        stopSpeaking()
        this.onFinal = onFinal; triedActivity = false
        heard = ""; reply = ""; level = 0f
        // The remote's microphone is a Bluetooth link; without it every recognizer hears silence.
        val remote = RemoteMic.read(ctx)
        if (remote.state == RemoteMic.State.DISCONNECTED) {
            Log.w(TAG, "remote mic not connected")
            phase = Phase.ERROR
            reply = "The remote's microphone isn't connected. Pair the remote in the TV's Settings › Remotes & accessories (its buttons work without that, the mic does not)."
            return
        }
        // Lumen listens with its own screen through Google's recognizer. If that recognizer gets
        // no words (on some TVs only Google's own voice screen is wired to the remote's mic), the
        // same attempt continues on Google's screen, and Lumen remembers which way worked.
        val own = prefs().getInt("voiceOwn", 0)   // 0 unknown, 1 Lumen's own listening works, -1 only Google's screen does
        if (own < 0 && captureWithActivity != null && !triedActivity) {
            triedActivity = true
            val launched = captureWithActivity?.invoke(voiceInputIntent()) ?: false
            Log.i(TAG, "capturing through Google's voice input (remembered): $launched")
            if (launched) { phase = Phase.LISTENING; return }
        }
        if (!available) { phase = Phase.ERROR; reply = "No speech recognizer on this TV."; return }
        recognizer?.destroy()
        // Google TV's own recognizer (Katniss) is the one that can open the remote's microphone.
        // The system default on this TV is the text-to-speech app's on-device recognizer, which
        // hears nothing from the remote and answers every attempt with "no match".
        val katniss = android.content.ComponentName(KATNISS, "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService")
        val useKatniss = runCatching { ctx.packageManager.getServiceInfo(katniss, 0) }.isSuccess
        val r = if (useKatniss) SpeechRecognizer.createSpeechRecognizer(ctx, katniss) else SpeechRecognizer.createSpeechRecognizer(ctx)
        Log.i(TAG, "listening with ${if (useKatniss) "katniss" else "system default"} recognizer")
        recognizer = r
        var gotAudio = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.i(TAG, "ready for speech"); phase = Phase.LISTENING }
            override fun onBeginningOfSpeech() { Log.i(TAG, "speech began"); gotAudio = true }
            override fun onRmsChanged(rmsdB: Float) { if (rmsdB > 0f) gotAudio = true; level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) { gotAudio = true }
            override fun onEndOfSpeech() { Log.i(TAG, "speech ended"); phase = Phase.THINKING }
            override fun onError(error: Int) {
                Log.w(TAG, "recognizer error $error (audio heard: $gotAudio)")
                // No words through Lumen's own listening: continue this attempt on Google's screen,
                // unless Lumen's own listening is known to work on this TV (then it was just silence).
                val noWords = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_AUDIO
                if (noWords && prefs().getInt("voiceOwn", 0) != 1 && !triedActivity) {
                    triedActivity = true
                    val launched = captureWithActivity?.invoke(voiceInputIntent()) ?: false
                    Log.i(TAG, "falling back to Google's voice input: $launched")
                    if (launched) { phase = Phase.LISTENING; return }
                }
                phase = Phase.ERROR
                reply = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Press OK and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                    SpeechRecognizer.ERROR_AUDIO -> "No microphone audio. Is this remote's mic enabled?"
                    else -> "Listening failed (error $error). Press OK to retry."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                Log.i(TAG, "heard: $text")
                if (text.isNotBlank()) prefs().edit().putInt("voiceOwn", 1).apply()
                heard = text
                phase = Phase.THINKING
                onFinal(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        phase = Phase.LISTENING
        runCatching { r.startListening(intent) }.onFailure { phase = Phase.ERROR; reply = "Could not start listening: ${it.message}" }
    }

    private fun prefs() = ctx.getSharedPreferences("voice", Context.MODE_PRIVATE)

    private fun voiceInputIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Two apps can answer this on Google TV; Google's is the one the remote's mic reaches.
        if (runCatching { ctx.packageManager.getPackageInfo(KATNISS, 0) }.isSuccess) setPackage(KATNISS)
    }

    /** What Google's voice-input screen heard (empty = nothing, or it was dismissed). */
    fun externalResult(text: String) {
        Log.i(TAG, "google voice input returned: '$text'")
        if (text.isBlank()) { phase = Phase.ERROR; reply = "I didn't catch that. Press OK and try again."; return }
        if (prefs().getInt("voiceOwn", 0) == 0) prefs().edit().putInt("voiceOwn", -1).apply()
        heard = text; phase = Phase.THINKING
        onFinal?.invoke(text)
    }

    fun speak(text: String) {
        reply = text
        phase = Phase.SPEAKING
        if (ttsReady) runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumen") }
    }

    fun stopSpeaking() { runCatching { tts?.stop() } }

    fun destroy() {
        runCatching { recognizer?.destroy() }; recognizer = null
        runCatching { tts?.shutdown() }; tts = null
    }

    // ------------------------------------------------------------ deciding what to do

    /** The launcher's own grammar (fast, offline); anything else goes to Google Assistant. */
    suspend fun decide(text: String, apps: List<TvApp>, inputs: List<TvInput>, weather: Weather?, subs: List<Subscription>, packages: List<String>): Action {
        val q = text.trim().lowercase()
        if (q.isBlank()) return Action.Say("I didn't hear anything.")
        if (q in setOf("close", "cancel", "never mind", "go home", "home")) return Action.Close

        Regex("^(?:open|launch|start|go to)\\s+(.+)$").find(q)?.groupValues?.get(1)?.let { name ->
            findApp(apps, name)?.let { return Action.OpenApp(it) }
            findInput(inputs, name)?.let { return Action.SwitchInput(it) }
        }
        Regex("^(?:switch|change|set)\\s+(?:the\\s+)?(?:input\\s+)?to\\s+(.+)$").find(q)?.groupValues?.get(1)?.let { name ->
            findInput(inputs, name)?.let { return Action.SwitchInput(it) }
            findApp(apps, name)?.let { return Action.OpenApp(it) }
        }
        if (Regex("\\b(xbox|hdmi\\s*\\d|av)\\b").containsMatchIn(q) && q.split(' ').size <= 4) {
            findInput(inputs, q)?.let { return Action.SwitchInput(it) }
        }
        if (Regex("\\b(weather|temperature|forecast|rain|hot|cold)\\b").containsMatchIn(q)) {
            weather ?: return Action.Say("I don't have the weather yet.")
            val tomorrow = weather.days.getOrNull(1)
            return Action.Say(
                if (q.contains("tomorrow") && tomorrow != null) "Tomorrow in ${weather.city}: ${wmoLabel(tomorrow.code)}, high ${tomorrow.hiC}, low ${tomorrow.loC}."
                else "${weather.city}: ${weather.tempC} degrees, ${weather.label.lowercase()}, feels like ${weather.feelsC}. High ${weather.hiC}, low ${weather.loC}.",
            )
        }
        if (Regex("\\b(time|date|day)\\b").containsMatchIn(q) && q.split(' ').size <= 6) {
            val now = Date()
            return Action.Say("It's ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)} on ${SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)}.")
        }
        if (Regex("\\b(subscription|subscriptions|renew|paying|spend)\\b").containsMatchIn(q)) {
            val total = subs.sumOf { it.monthlyInr }
            val next = subs.mapNotNull { s -> s.nextRenewal()?.let { s to it } }.minByOrNull { it.second }
            return Action.Say(
                if (total == 0) "No paid subscriptions are set up yet."
                else "You pay about $total rupees a month." + (next?.let { " ${it.first.name} renews on ${it.second.dayOfMonth} ${it.second.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }}." } ?: ""),
            )
        }
        Regex("^(?:volume|sound)\\s+(up|down|mute|unmute)$").find(q)?.groupValues?.get(1)?.let { dir ->
            val am = ctx.getSystemService(AudioManager::class.java)
            val d = when (dir) { "up" -> AudioManager.ADJUST_RAISE; "down" -> AudioManager.ADJUST_LOWER; "mute" -> AudioManager.ADJUST_MUTE; else -> AudioManager.ADJUST_UNMUTE }
            runCatching { am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI) }
            return Action.Say("Volume $dir.")
        }
        Regex("^(?:play|watch|find|search(?: for)?|show me)\\s+(.+)$").find(q)?.groupValues?.get(1)?.let { what ->
            val cleaned = what.replace(Regex("\\s+on\\s+(netflix|prime|hotstar|jiohotstar|sony|zee5|youtube)$"), "")
            findApp(apps, cleaned)?.let { return Action.OpenApp(it) }
            val hits = runCatching { JustWatch.search(ctx, cleaned, packages) }.getOrDefault(emptyList())
            hits.firstOrNull()?.let { return Action.ShowTitle(it) }
            return Action.SearchTv(cleaned)
        }
        findApp(apps, q)?.let { if (q.split(' ').size <= 3) return Action.OpenApp(it) }

        return Action.AskGoogle(text.trim())
    }

    private fun findApp(apps: List<TvApp>, name: String): TvApp? {
        val n = name.trim().lowercase().removeSuffix(" app")
        val aliases = mapOf("prime" to "prime video", "amazon" to "prime video", "hotstar" to "jiohotstar", "jio" to "jiohotstar", "sony" to "sony liv", "you tube" to "youtube", "play store" to "play store")
        val key = aliases[n] ?: n
        return apps.firstOrNull { it.label.lowercase() == key }
            ?: apps.firstOrNull { it.label.lowercase().contains(key) || key.contains(it.label.lowercase()) }
    }

    private fun findInput(inputs: List<TvInput>, name: String): TvInput? {
        val n = name.lowercase()
        if (n.contains("xbox") || n.contains("console")) return inputs.firstOrNull { it.label.contains("xbox", ignoreCase = true) } ?: inputs.firstOrNull { it.connected } ?: inputs.firstOrNull { it.port.contains("HDMI 1") }
        val num = Regex("hdmi\\s*(\\d)").find(n)?.groupValues?.get(1)
        if (num != null) return inputs.firstOrNull { it.port.replace(" ", "").equals("HDMI$num", ignoreCase = true) }
        if (n.contains("av")) return inputs.firstOrNull { it.port.equals("AV", ignoreCase = true) }
        return null
    }
}
