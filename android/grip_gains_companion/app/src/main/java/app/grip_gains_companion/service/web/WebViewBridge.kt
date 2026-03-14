package app.grip_gains_companion.service.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebViewBridge {
    private var webView: WebView? = null

    private val _sessionGripper = MutableStateFlow<String?>("")
    val sessionGripper = _sessionGripper.asStateFlow()

    private val _sessionSide = MutableStateFlow<String?>("Bilateral")
    val sessionSide = _sessionSide.asStateFlow()

    private val _manualSessionEndTrigger = MutableStateFlow(false)
    val manualSessionEndTrigger = _manualSessionEndTrigger.asStateFlow()

    // UI & Navigation State
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible = _isToolbarVisible.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl = _currentUrl.asStateFlow()

    // Timer & Data State
    private val _remainingTime = MutableStateFlow<Int?>(null)
    val remainingTime = _remainingTime.asStateFlow()

    private val _buttonEnabled = MutableStateFlow(false)
    val buttonEnabled = _buttonEnabled.asStateFlow()

    private val _targetWeight = MutableStateFlow<Double?>(null)
    val targetWeight = _targetWeight.asStateFlow()

    private val _targetDuration = MutableStateFlow<Int?>(null)
    val targetDuration = _targetDuration.asStateFlow()

    private val _saveButtonAppeared = MutableStateFlow(false)
    val saveButtonAppeared = _saveButtonAppeared.asStateFlow()

    private val _latestRepStats = MutableStateFlow<String?>(null)
    val latestRepStats = _latestRepStats.asStateFlow()

    // Methods for UI to call
    fun setWebView(view: WebView) { this.webView = view }

    fun updateHistoryState(back: Boolean, forward: Boolean) {
        _canGoBack.value = back
        _canGoForward.value = forward
    }

    fun setToolbarVisible(visible: Boolean) { _isToolbarVisible.value = visible }
    fun goBack() { webView?.goBack() }
    fun goForward() { webView?.goForward() }
    fun reloadPage() { webView?.post { webView?.reload() } }

    fun clearWebsiteData() {
        webView?.post {
            webView?.clearCache(true)
            webView?.clearHistory()
            android.webkit.WebStorage.getInstance().deleteAllData()
            webView?.reload()
        }
    }

    fun clickFailButton() {
        webView?.post {
            webView?.evaluateJavascript(JavaScriptBridge.clickFailButton, null)
        }
    }

    fun resetSaveFlag() {
        _saveButtonAppeared.value = false
    }

    fun clickEndSessionButton() {
        val js = """
            (function() {
                let attempts = 0;
                let clicker = setInterval(() => {
                    let btn = document.querySelector('button.btn-danger.btn-lg.session-actions-end');
                    if (btn && !btn.disabled) { 
                        btn.click(); 
                        clearInterval(clicker);
                    }
                    if (++attempts > 20) clearInterval(clicker);
                }, 100);
            })();
        """.trimIndent()
        webView?.post { webView?.evaluateJavascript(js, null) }
    }

    // =========================================================================
    // JAVASCRIPT INTERFACES
    // =========================================================================

    @JavascriptInterface
    fun onTargetDurationChanged(seconds: Int) {
        _targetDuration.value = if (seconds == -1) null else seconds
    }

    @JavascriptInterface
    fun onSessionManuallyEnded() {
        _manualSessionEndTrigger.value = true
    }

    fun resetManualSessionEndTrigger() {
        _manualSessionEndTrigger.value = false
    }

    @JavascriptInterface
    fun updateUrl(url: String) {
        _currentUrl.value = url
    }

    @JavascriptInterface
    fun onSessionInfoChanged(gripper: String?, side: String?) {
        _sessionGripper.value = gripper
        _sessionSide.value = side
    }

    @JavascriptInterface
    fun onRemainingTimeChanged(seconds: Int) {
        _remainingTime.value = seconds
    }

    @JavascriptInterface
    fun onButtonStateChanged(isEnabled: Boolean) {
        _buttonEnabled.value = isEnabled
    }

    @JavascriptInterface
    fun onTargetWeightChanged(weightStr: String?) {
        if (weightStr == null) {
            _targetWeight.value = null
        } else {
            val lowercased = weightStr.lowercase()
            val isLbs = lowercased.contains("lbs") || lowercased.contains("lb")
            val digits = lowercased.replace(Regex("[^0-9.]"), "")
            val value = digits.toDoubleOrNull()

            if (value != null) {
                _targetWeight.value = if (isLbs) value / 2.20462 else value
            } else {
                _targetWeight.value = null
            }
        }
    }

    @JavascriptInterface
    fun onSaveButtonAppeared() {
        _saveButtonAppeared.value = true
    }

    @JavascriptInterface
    fun onRepStatsExtracted(jsonString: String) {
        _latestRepStats.value = jsonString
    }

    @JavascriptInterface
    fun onSettingsVisibleChanged(isVisible: Boolean) { }

    @JavascriptInterface
    fun onWeightOptionsChanged(jsonArrayStr: String, isLbs: Boolean) { }

    // ---> NEW: SCROLL VELOCITY DETECTOR <---
    @JavascriptInterface
    fun onScrollVelocityDetected(isScrollingDown: Boolean) {
        // If scrolling down fast, hide toolbar (false). If scrolling up fast, show toolbar (true).
        _isToolbarVisible.value = !isScrollingDown
    }
}