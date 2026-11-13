package com.buildai.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeneratedAppValidationException(message: String, val diagnostic: String) : IllegalArgumentException(message)

class GeneratedAppValidator(private val context: Context) {
    /** Smoke check, not a proof that all requested features work. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun validate(html: String) = checkPage(html, null)

    suspend fun validateInteraction(html: String, probe: String) = checkPage(html, probe)

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun checkPage(html: String, probe: String?) {
        try {
            withTimeout(15_000) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val view = WebView(context)
                        val handler = Handler(Looper.getMainLooper())
                        val origin = "https://check-${UUID.randomUUID()}.buildai.local/"
                        var scriptError: String? = null
                        var finished = false
                        var disposed = false
                        fun dispose() {
                            if (disposed) return
                            disposed = true
                            handler.removeCallbacksAndMessages(null)
                            view.stopLoading(); view.destroy()
                            android.webkit.WebStorage.getInstance().deleteOrigin(origin.removeSuffix("/"))
                        }
                        continuation.invokeOnCancellation { handler.post { dispose() } }
                        view.settings.javaScriptEnabled = true
                        view.settings.domStorageEnabled = true
                        view.settings.allowFileAccess = false
                        view.settings.allowContentAccess = false
                        view.settings.blockNetworkLoads = true
                        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) scriptError = message.message().take(240)
                                return true
                            }
                        }
                        view.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(webView: WebView, url: String) {
                                if (finished || !continuation.isActive) return
                                finished = true
                                fun checkVisible(remaining: Int) {
                                    if (!continuation.isActive) { dispose(); return }
                                    view.evaluateJavascript("""
                                        (() => {
                                            try {
                                                const key = '__buildai_check_${UUID.randomUUID()}';
                                                localStorage.setItem(key, 'ok');
                                                const storageWorks = localStorage.getItem(key) === 'ok';
                                                localStorage.removeItem(key);
                                                const visible = [...document.body.querySelectorAll('*')].some(el => {
                                                    const content = el.children.length === 0 && el.textContent.trim().length > 0;
                                                    const control = ['INPUT','TEXTAREA','BUTTON','CANVAS','SVG','IMG'].includes(el.tagName);
                                                    if (!content && !control) return false;
                                                    for (let parent = el; parent; parent = parent.parentElement) {
                                                        const style = getComputedStyle(parent);
                                                        if (parent.hidden || style.display === 'none' ||
                                                            style.visibility === 'hidden' || Number(style.opacity) === 0)
                                                            return false;
                                                    }
                                                    return true;
                                                });
                                                return !!document.body && storageWorks && !window.__buildaiError && visible;
                                            } catch (e) { return false; }
                                        })()
                                    """.trimIndent()) { result ->
                                        if (!continuation.isActive) { dispose(); return@evaluateJavascript }
                                        when {
                                            scriptError == null && result == "true" -> {
                                                if (probe == null) {
                                                    continuation.resume(Unit); dispose()
                                                } else view.evaluateJavascript(probe) interaction@ { checked ->
                                                    if (!continuation.isActive) { dispose(); return@interaction }
                                                    if (checked == "true" && scriptError == null) continuation.resume(Unit)
                                                    else continuation.resumeWithException(GeneratedAppValidationException(
                                                        "A requested app feature did not work during its check. Try revising the plan.",
                                                        scriptError ?: checked.take(500)))
                                                    dispose()
                                                }
                                            }
                                            scriptError != null || remaining <= 0 -> {
                                                continuation.resumeWithException(GeneratedAppValidationException(
                                                    "The generated app opened blank or failed to run. Try again or simplify the plan.",
                                                    scriptError ?: "Page has no visible content, storage failed, or a startup error occurred."))
                                                dispose()
                                            }
                                            else -> handler.postDelayed({ checkVisible(remaining - 1) }, 250)
                                        }
                                    }
                                }
                                handler.postDelayed({ checkVisible(16) }, 250)
                            }
                        }
                        val bootstrap = """<script>window.addEventListener('error',e=>{window.__buildaiError=true;window.__buildaiErrorDetail=e.message});window.addEventListener('unhandledrejection',e=>{window.__buildaiError=true;window.__buildaiErrorDetail=String(e.reason)});</script>"""
                        val instrumented = html.replaceFirst(Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE), "$0$bootstrap")
                        view.loadDataWithBaseURL(origin, instrumented, "text/html", "UTF-8", null)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalArgumentException("The generated app took too long to open. Try again.")
        }
    }
}
