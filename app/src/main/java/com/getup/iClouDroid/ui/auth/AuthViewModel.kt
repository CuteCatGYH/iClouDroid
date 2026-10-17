package com.getup.iClouDroid.ui.auth

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getup.iClouDroid.data.session.SessionManager
import com.getup.icloudcore.Icloudcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface AuthUiState {
    data class NotLoggedIn(val isChinaMainland: Boolean = true) : AuthUiState
    data class Authenticating(val message: String) : AuthUiState
    data class LoggedIn(
        val dsid: String,
        val ckDatabaseUrl: String,
        val isChinaMainland: Boolean,
        val hasHsaTrust: Boolean
    ) : AuthUiState
    data class Error(val errorMessage: String, val isChinaMainland: Boolean) : AuthUiState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.NotLoggedIn())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Latest non-fatal cookie probe, surfaced in the diagnostic panel before full success. */
    private val _cookieDiagnostic = MutableStateFlow<CookieValidationResult?>(null)
    val cookieDiagnostic: StateFlow<CookieValidationResult?> = _cookieDiagnostic.asStateFlow()

    /** Bound by [ICloudWebView]; lets the UI force an extraction when auto-detection misses. */
    private val _cookieProbe = CookieProbe()
    val cookieProbe: CookieProbe get() = _cookieProbe

    init {
        // Run session restoration in background coroutine to avoid cold-start jank (skipping frames)
        viewModelScope.launch {
            checkExistingSession()
        }
    }

    suspend fun checkExistingSession() {
        val isLoggedIn = withContext(Dispatchers.IO) { sessionManager.isLoggedIn() }
        if (isLoggedIn) {
            val cookieStr = withContext(Dispatchers.IO) { sessionManager.getCookieString() ?: "" }
            val isChina = withContext(Dispatchers.IO) { sessionManager.isChinaMainland() }
            val savedDsid = withContext(Dispatchers.IO) { sessionManager.getDsid() ?: "" }
            val savedCkUrl = withContext(Dispatchers.IO) { sessionManager.getCkDatabaseUrl() ?: "" }

            if (savedDsid.isNotBlank() && savedCkUrl.isNotBlank()) {
                val validation = CookieValidationResult.validate(cookieStr)
                validation.logToLogcat("restored-from-storage")
                _uiState.value = AuthUiState.LoggedIn(
                    dsid = savedDsid,
                    ckDatabaseUrl = savedCkUrl,
                    isChinaMainland = isChina,
                    hasHsaTrust = validation.hasHsaTrust
                )
            } else {
                // Initialize session via Go core
                initGoSession(cookieStr, isChina, CookieValidationResult.validate(cookieStr).hasHsaTrust)
            }
        } else {
            val isChina = withContext(Dispatchers.IO) { sessionManager.isChinaMainland() }
            _uiState.value = AuthUiState.NotLoggedIn(isChina)
        }
    }

    fun setRegion(isChinaMainland: Boolean) {
        sessionManager.setChinaMainland(isChinaMainland)
        _cookieDiagnostic.value = null
        val currentState = _uiState.value
        if (currentState is AuthUiState.NotLoggedIn) {
            _uiState.value = AuthUiState.NotLoggedIn(isChinaMainland)
        } else if (currentState is AuthUiState.Error) {
            _uiState.value = AuthUiState.NotLoggedIn(isChinaMainland)
        }
    }

    fun onCookiesExtracted(result: CookieValidationResult) {
        if (_uiState.value is AuthUiState.Authenticating || _uiState.value is AuthUiState.LoggedIn) {
            return
        }

        // Always rely on the explicitly stored preference as the single source of truth
        val isChina = sessionManager.isChinaMainland()

        Log.i(AUTH_LOG_TAG, "Persisting cookie set (region=${if (isChina) "cn" else "global"})")
        sessionManager.saveSession(
            cookieStr = result.cookieString,
            isChinaMainland = isChina
        )

        initGoSession(result.cookieString, isChina, result.hasHsaTrust)
    }

    /** Receives every incomplete probe so the UI can explain what is still missing. */
    fun onCookieDiagnostic(result: CookieValidationResult) {
        _cookieDiagnostic.value = result
    }

    /**
     * Manual fallback: pull cookies straight out of the live WebView and run the same
     * three-element validation. Used when auto-detection is blocked by an unknown redirect.
     */
    fun forceExtractCookies() {
        if (_uiState.value is AuthUiState.Authenticating || _uiState.value is AuthUiState.LoggedIn) {
            return
        }
        val probeResult = _cookieProbe.extract()
        Log.i(AUTH_LOG_TAG, "forceExtractCookies invoked, result=${probeResult?.missingKeys}")
        if (probeResult == null) {
            _cookieDiagnostic.value = null
            _uiState.value = AuthUiState.Error(
                errorMessage = "WebView 尚未初始化或已销毁，请重新加载登录页后再试。",
                isChinaMainland = sessionManager.isChinaMainland()
            )
            return
        }
        if (probeResult.isValid) {
            onCookiesExtracted(probeResult)
        } else {
            _cookieDiagnostic.value = probeResult
        }
    }

    private fun initGoSession(cookieStr: String, isChinaMainland: Boolean, hasHsaTrust: Boolean) {
        _uiState.value = AuthUiState.Authenticating("正在请求 iCloud 动态路由表...")
        Log.i(AUTH_LOG_TAG, "Calling Go InitSession (isChinaMainland=$isChinaMainland, hasHsaTrust=$hasHsaTrust)")

        viewModelScope.launch {
            try {
                val jsonResult = withContext(Dispatchers.IO) {
                    Icloudcore.initSession(cookieStr, isChinaMainland)
                }
                Log.i(AUTH_LOG_TAG, "Go InitSession returned: $jsonResult")

                val json = JSONObject(jsonResult)
                val success = json.optBoolean("success", false)

                if (success) {
                    val dsid = json.optString("dsid", "未知 DSID")
                    val ckUrl = json.optString("ckDatabaseUrl", "未获取到 CloudKit 节点")

                    sessionManager.updateRouteInfo(dsid, ckUrl)

                    // Check if Go core refreshed cookies silently
                    val updatedCookies = withContext(Dispatchers.IO) {
                        if (Icloudcore.hasCookieUpdates()) {
                            Icloudcore.getCurrentCookieString()
                        } else null
                    }
                    if (!updatedCookies.isNullOrBlank()) {
                        Log.i(AUTH_LOG_TAG, "Go core returned updated cookies, persisting...")
                        sessionManager.saveSession(
                            cookieStr = updatedCookies,
                            isChinaMainland = isChinaMainland,
                            dsid = dsid,
                            ckDatabaseUrl = ckUrl
                        )
                    }

                    _uiState.value = AuthUiState.LoggedIn(
                        dsid = dsid,
                        ckDatabaseUrl = ckUrl,
                        isChinaMainland = isChinaMainland,
                        hasHsaTrust = hasHsaTrust
                    )
                } else {
                    val errMsg = json.optString("errorMessage", "iCloud 登录初始化失败，请重试")
                    Log.w(AUTH_LOG_TAG, "Go InitSession reported failure: $errMsg")
                    _uiState.value = AuthUiState.Error(errMsg, isChinaMainland)
                }
            } catch (e: Exception) {
                Log.e(AUTH_LOG_TAG, "Go InitSession threw", e)
                _uiState.value = AuthUiState.Error("系统异常: ${e.message}", isChinaMainland)
            }
        }
    }

    fun logout() {
        val currentRegion = sessionManager.isChinaMainland()
        sessionManager.clearSession()
        _cookieDiagnostic.value = null

        // Clear WebView cookies & WebStorage so the user can sign in with another account or region
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                WebStorage.getInstance().deleteAllData()
                Log.i(AUTH_LOG_TAG, "Session cleared, WebView cookies dropped.")
            } catch (e: Exception) {
                // Ignore if WebView is not initialized
            }
        }

        _uiState.value = AuthUiState.NotLoggedIn(isChinaMainland = currentRegion)
    }
}
