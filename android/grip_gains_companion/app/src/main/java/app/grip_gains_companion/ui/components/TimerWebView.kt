package app.grip_gains_companion.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.grip_gains_companion.service.web.JavaScriptBridge
import app.grip_gains_companion.service.web.WebViewBridge

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TimerWebView(
    bridge: WebViewBridge,
    cachedWebView: android.webkit.WebView,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = {
            // USE the cachedWebView passed in, DO NOT instantiate a new WebView(context)
            cachedWebView.apply {
                // Detach from any previous parent when returning from another Compose screen
                (parent as? ViewGroup)?.removeView(this)

                bridge.setWebView(this)

                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val isScrollingDown = scrollY > oldScrollY
                    val isAtTop = scrollY < 50

                    if (isScrollingDown && !isAtTop) {
                        bridge.setToolbarVisible(false)
                    } else if (oldScrollY > scrollY || isAtTop) {
                        bridge.setToolbarVisible(true)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { bridge.updateUrl(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        bridge.updateHistoryState(canGoBack(), canGoForward())

                        view?.evaluateJavascript(JavaScriptBridge.remainingTimeObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.observerScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.targetWeightObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.saveButtonObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.basicTimerEndObserverScript, null)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        bridge.updateHistoryState(canGoBack(), canGoForward())
                        url?.let { bridge.updateUrl(it) }

                        // FORCE Passive Observers to start whenever you switch pages on the site
                        view?.evaluateJavascript(JavaScriptBridge.targetWeightObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.basicTimerEndObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.saveButtonObserverScript, null)
                        view?.evaluateJavascript(JavaScriptBridge.observerScript, null)
                    }}
                // Only load a URL if the WebView is completely blank (first launch)
                if (url == null) {
                    loadUrl("https://gripgains.ca")
                }
            }
        }
    )
}