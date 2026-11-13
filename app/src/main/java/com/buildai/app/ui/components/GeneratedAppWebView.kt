package com.buildai.app.ui.components

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import com.buildai.app.data.storage.WidgetSummaryStore
import com.buildai.app.widgets.ProjectWidgetProvider
import org.json.JSONObject
import android.widget.EditText

fun generatedAppOrigin(projectId: String): String {
    val hostId = java.security.MessageDigest.getInstance("SHA-256")
        .digest(projectId.toByteArray(Charsets.UTF_8)).take(20)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return "https://project-$hostId.buildai.local/"
}

/** Every project has its own storage origin; generated pages cannot access files or the network. */
@SuppressLint("SetJavaScriptEnabled")
class GeneratedAppWebView(context: Context) : WebView(context) {
    private var loadedHtml: String? = null
    @Volatile private var loadedProject: String? = null
    private var dialog: AlertDialog? = null
    private var reloadToken: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    var onPreviewIssue: ((String?) -> Unit)? = null

    private inner class SummaryBridge {
        @JavascriptInterface fun updateSummary(json: String, projectId: String) {
            if (projectId != loadedProject) return
            if (json.length > 256) return
            val values = runCatching { JSONObject(json) }.getOrNull() ?: return
            if (!values.has("total") || !values.has("done")) return
            WidgetSummaryStore.write(context.applicationContext, projectId,
                values.optInt("total", 0), values.optInt("done", 0))
            handler.post { ProjectWidgetProvider.refreshProject(context.applicationContext, projectId) }
        }
    }

    init {
        addJavascriptInterface(SummaryBridge(), "BuildAIWidget")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != loadedProject?.let { android.net.Uri.parse(generatedAppOrigin(it)).host }

            override fun onReceivedError(view: WebView, request: WebResourceRequest,
                error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) onPreviewIssue?.invoke("The preview could not open this page.")
            }

            override fun onPageFinished(view: WebView, url: String) {
                handler.postDelayed({
                    if (loadedHtml != null) evaluateJavascript("""
                        (() => {
                            if (!document.body) return false;
                            const hasText = document.body.innerText.trim().length > 0;
                            const hasControls = [...document.querySelectorAll('input,textarea,button,canvas,svg,img')]
                                .some(el => { const r = el.getBoundingClientRect();
                                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < innerHeight; });
                            return hasText || hasControls;
                        })()
                    """.trimIndent()) { visible ->
                        onPreviewIssue?.invoke(if (visible == "true") null
                            else "The preview appears blank. Try reloading or regenerate this app.")
                    }
                }, 900)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                dialog = AlertDialog.Builder(context).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                dialog = AlertDialog.Builder(context).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                val input = EditText(context).apply { setText(defaultValue) }
                dialog = AlertDialog.Builder(context).setMessage(message).setView(input)
                    .setPositiveButton("OK") { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
    }

    fun loadApp(html: String, projectId: String, token: Int = 0) {
        if (loadedHtml == html && loadedProject == projectId && reloadToken == token) return
        loadedHtml = html; loadedProject = projectId; reloadToken = token
        onPreviewIssue?.invoke(null)
        val script = "<script>window.__buildaiProjectId=${JSONObject.quote(projectId)};</script>"
        val instrumented = html.replaceFirst(Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE), "$0$script")
        loadDataWithBaseURL(generatedAppOrigin(projectId), instrumented, "text/html", "UTF-8", null)
    }

    override fun destroy() {
        handler.removeCallbacksAndMessages(null)
        dialog?.cancel(); dialog = null; stopLoading(); super.destroy()
    }
}
