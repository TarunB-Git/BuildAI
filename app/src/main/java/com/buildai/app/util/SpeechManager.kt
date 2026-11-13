package com.buildai.app.util

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

// ── State ────────────────────────────────────────────────────────────────────

sealed class SpeechState {
    object Idle       : SpeechState()
    object Listening  : SpeechState()
    object Processing : SpeechState()
    data class Partial(val text: String) : SpeechState()
    data class Result(val text: String)  : SpeechState()
    data class Error(val message: String): SpeechState()
}

// ── Manager ──────────────────────────────────────────────────────────────────

class SpeechManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private val settings = com.buildai.app.data.storage.SecureStorage(context)
    private var generation = 0L

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _state.asStateFlow()

    val isAvailable: Boolean
        get() = if (settings.getChatBackend() == "local")
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        else SpeechRecognizer.isRecognitionAvailable(context)

    fun permissionDenied() { _state.value = SpeechState.Error("Microphone permission is required for voice input. You can still type your idea.") }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied(); return
        }
        if (!isAvailable) {
            _state.value = SpeechState.Error("On-device speech isn't available in local mode. You can still type.")
            return
        }

        cancelListening()
        val epoch = generation
        val localOnly = settings.getChatBackend() == "local"
        recognizer = (if (localOnly) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)).apply {
            setRecognitionListener(listener(epoch))
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        _state.value = SpeechState.Idle
    }

    fun resetToIdle() {
        _state.value = SpeechState.Idle
    }

    fun cancelListening() {
        generation++
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _state.value = SpeechState.Idle
    }

    fun destroy() = cancelListening()

    // ── RecognitionListener ──────────────────────────────────────────────────

    private fun listener(epoch: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?)  { if (epoch == generation) _state.value = SpeechState.Listening }
        override fun onBeginningOfSpeech()         { if (epoch == generation) _state.value = SpeechState.Listening }
        override fun onRmsChanged(v: Float)        { /* could drive a volume visualizer */ }
        override fun onBufferReceived(b: ByteArray?){}
        override fun onEndOfSpeech()               { if (epoch == generation) _state.value = SpeechState.Processing }

        override fun onPartialResults(bundle: Bundle?) {
            if (epoch != generation) return
            val partial = bundle
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            if (partial.isNotBlank()) _state.value = SpeechState.Partial(partial)
        }

        override fun onResults(bundle: Bundle?) {
            if (epoch != generation) return
            val text = bundle
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            _state.value = SpeechState.Result(text)
        }

        override fun onError(code: Int) {
            if (epoch != generation) return
            val msg = when (code) {
                SpeechRecognizer.ERROR_NO_MATCH           -> "Couldn't catch that — please try again"
                SpeechRecognizer.ERROR_NETWORK            -> "No internet — check your connection"
                SpeechRecognizer.ERROR_AUDIO              -> "Audio problem — try again"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "No speech detected — tap and speak"
                else                                      -> "Something went wrong — try again"
            }
            _state.value = SpeechState.Error(msg)
        }

        override fun onEvent(type: Int, params: Bundle?) {}
    }
}
