package com.fba.app.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fba.app.data.auth.AuthRepository
import com.fba.app.data.auth.SessionCookieStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    /** null = in progress, true = logged in, false = failed */
    private val _result = MutableStateFlow<Boolean?>(null)
    val result: StateFlow<Boolean?> = _result
    private var completing = false

    /** Called with the WebView's cookies each time it lands on the FBA site. */
    fun onSiteCookies(header: String?) {
        if (completing) return
        val cookies = SessionCookieStore.parseCookieHeader(header)
        if (!SessionCookieStore.isCompleteSession(cookies)) return
        completing = true
        viewModelScope.launch {
            val ok = auth.completeLoginFromWebView(cookies)
            _result.value = ok
            if (!ok) completing = false
        }
    }
}

/**
 * Logs in with the FBA / Triratna account using the website's own login page.
 * The site redirects to sso.triratna.co and back; once it lands on
 * freebuddhistaudio.com with a complete session we copy the cookies into the
 * app and leave.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onDone: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var finishing by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        if (result == true) onDone()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Log in to FBA") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (result == false) {
                Text(
                    "Couldn't complete the login. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        // Start from a clean browser session so the SSO form shows,
                        // rather than silently reusing a previous account.
                        CookieManager.getInstance().removeAllCookies(null)
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    if (url == null) return
                                    val onSite = url.startsWith("https://${SessionCookieStore.HOST}") && !url.contains("/sso/")
                                    if (onSite) {
                                        val header = CookieManager.getInstance().getCookie("https://${SessionCookieStore.HOST}")
                                        if (SessionCookieStore.isCompleteSession(SessionCookieStore.parseCookieHeader(header))) {
                                            finishing = true
                                        }
                                        viewModel.onSiteCookies(header)
                                    }
                                }
                            }
                            loadUrl(AuthRepository.LOGIN_URL)
                        }
                    },
                )
                if (finishing && result != false) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}
