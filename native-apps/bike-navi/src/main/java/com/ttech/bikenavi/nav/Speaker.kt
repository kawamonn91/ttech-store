package com.ttech.bikenavi.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * 端末の音声合成(日本語)で、案内を読み上げる。
 *  - 案内用の音声として鳴らし、読み上げている間だけ、音楽などの音量を下げる(オーディオフォーカス)
 *  - 読み上げが間に合わず、古くなった案内(すでに曲がり角を過ぎたなど)は、読まずに捨てる
 * どの操作も、メインスレッドから呼ぶこと。
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private class Utterance(val text: String, val expiresAtMs: Long)

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val queue = ArrayDeque<Utterance>()
    private var ready = false
    private var speaking = false
    private var focus: AudioFocusRequest? = null

    /** false にすると、読み上げない(音声の案内をオフにしたとき) */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stopNow()
        }

    var rate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            NavState.voiceAvailable.value = false
            return
        }
        val result = engine.setLanguage(Locale.JAPAN)
        val usable = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        NavState.voiceAvailable.value = usable
        if (!usable) return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setSpeechRate(rate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { finished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { finished() }
            }
        })
        ready = true
        handler.post(::pump)
    }

    /** 読み上げを頼む。[ttlMs] のあいだに読み始められなければ、捨てる */
    fun speak(text: String, ttlMs: Long = 15_000L) {
        Log.i(TAG, "案内: $text")
        if (!enabled) return
        queue.add(Utterance(text, System.currentTimeMillis() + ttlMs))
        pump()
    }

    private fun pump() {
        if (!ready || speaking || !enabled) return
        val engine = tts ?: return
        while (true) {
            val next = queue.poll() ?: run {
                abandonFocus()
                return
            }
            if (next.expiresAtMs < System.currentTimeMillis()) continue
            speaking = true
            requestFocus()
            val result = engine.speak(next.text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            if (result != TextToSpeech.SUCCESS) {
                speaking = false
                continue
            }
            return
        }
    }

    private fun finished() {
        speaking = false
        pump()
    }

    private fun stopNow() {
        queue.clear()
        tts?.stop()
        speaking = false
        abandonFocus()
    }

    private fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focus = request
        audio.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun shutdown() {
        stopNow()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "BikeNaviSpeaker"
    }
}
