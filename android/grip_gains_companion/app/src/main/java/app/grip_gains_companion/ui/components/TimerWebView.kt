package app.grip_gains_companion.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.service.web.JavaScriptBridge
import app.grip_gains_companion.service.web.WebViewBridge

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TimerWebView(
    webViewBridge: WebViewBridge,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webBackgroundColor = Color(0xFF1A2231)

    var pageLoaded by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "WebViewFade"
    )

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#1A2231"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = false
                allowContentAccess = false
            }

            addJavascriptInterface(webViewBridge, "Android")
            webViewBridge.setWebView(this)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(JavaScriptBridge.backgroundTimeOffsetScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.closePickerOnLoadScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.observerScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.targetWeightObserverScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.remainingTimeObserverScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.settingsVisibilityObserverScript, null)
                    view?.evaluateJavascript(JavaScriptBridge.saveButtonObserverScript, null)

                    pageLoaded = true // Trigger the fade-in animation
                }
            }

            loadUrl(AppConstants.GRIP_GAINS_URL)
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Box(modifier = modifier.background(webBackgroundColor)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().alpha(alpha)
        )
    }
}