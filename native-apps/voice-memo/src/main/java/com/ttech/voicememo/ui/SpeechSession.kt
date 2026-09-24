package com.ttech.voicememo.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Error(val message: String) : SpeechEvent

    /** 聞き取りが完全に終わった(停止操作・エラーのどちらでも1回だけ届く)。 */
    data object Ended : SpeechEvent
}

/**
 * Androidの音声認識(SpeechRecognizer)を「停止するまで続ける」形で使う。
 * SpeechRecognizerは1回の発話で終わってしまうため、区切りごとに確定して聞き取りを再開する。
 * メインスレッドから呼ぶこと。
 */
class SpeechSession(private val context: Context, private val emit: (SpeechEvent) -> Unit) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    fun start() {
        if (active) return
        active = true
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(listener)
        recognizer = created
        created.startListening(intent)
    }

    /** 話し終えた分の結果を受け取ってから終了する。 */
    fun stop() {
        if (!active) return
        active = false
        recognizer?.stopListening()
    }

    fun destroy() {
        active = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun finish() {
        recognizer?.destroy()
        recognizer = null
        active = false
        emit(SpeechEvent.Ended)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { emit(SpeechEvent.Partial(it)) }
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.let { emit(SpeechEvent.Final(it)) }
            if (active) recognizer?.startListening(intent) else finish()
        }

        override fun onError(error: Int) {
            val silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            when {
                !active -> finish()
                silent -> recognizer?.startListening(intent)
                else -> {
                    emit(SpeechEvent.Error(errorMessage(error)))
                    finish()
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotEmpty() }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイクの許可が必要です"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークに接続できません"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です。少し待ってからもう一度お試しください"
        else -> "音声認識を続けられませんでした(コード$error)"
    }
}
