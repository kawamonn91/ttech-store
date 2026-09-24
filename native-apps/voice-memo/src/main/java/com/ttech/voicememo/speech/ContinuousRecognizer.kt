package com.ttech.voicememo.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 端末標準の音声認識で日本語を文字起こしする。
 * SpeechRecognizer は無音が続くと1回で終わってしまうため、[stop] が呼ばれるまで自動で聞き取りを再開し、
 * 確定した文を順につなげていく(Webアプリ版の continuous = true に相当)。
 */
class ContinuousRecognizer(
    context: Context,
    /** 確定した文(これまでの合計)と、認識途中の文 */
    private val onText: (committed: String, partial: String) -> Unit,
    /** 聞き取りを終えたとき。エラーで止まった場合はメッセージ付き */
    private val onStopped: (error: String?) -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var committed = ""
    private var listening = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                onText(committed, firstResult(partialResults))
            }

            override fun onResults(results: Bundle) {
                committed += firstResult(results)
                onText(committed, "")
                if (listening) recognizer.startListening(intent) else onStopped(null)
            }

            override fun onError(error: Int) {
                when {
                    // 聞き取れなかっただけなら、録音中は続けて聞き取る
                    listening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ->
                        recognizer.startListening(intent)
                    !listening -> onStopped(null)
                    else -> {
                        listening = false
                        onStopped(errorMessage(error))
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    /** 聞き取りを始める。[initialText] に続けて文字起こしする。 */
    fun start(initialText: String) {
        committed = initialText
        listening = true
        recognizer.startListening(intent)
    }

    fun stop() {
        listening = false
        recognizer.stopListening()
    }

    fun destroy() {
        listening = false
        recognizer.destroy()
    }

    private fun firstResult(bundle: Bundle): String =
        bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイクの使用が許可されていません"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークに接続できませんでした"
        SpeechRecognizer.ERROR_AUDIO -> "録音できませんでした"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です。少し待ってからお試しください"
        else -> "音声認識を続けられませんでした(エラー $error)"
    }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
