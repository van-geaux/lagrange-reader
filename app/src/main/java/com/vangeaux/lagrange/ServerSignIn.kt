package com.vangeaux.lagrange

import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

internal fun buildServerLoginUrl(serverUrl: String): String? {
    val trimmed = serverUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) {
        return null
    }
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) {
        return null
    }
    if (uri.host.isNullOrBlank()) {
        return null
    }
    return "$trimmed/login"
}

internal fun isSameOrigin(pageUrl: String, configuredServerUrl: String): Boolean {
    val page = runCatching { URI(pageUrl) }.getOrNull() ?: return false
    val server = runCatching { URI(configuredServerUrl) }.getOrNull() ?: return false
    val pageScheme = page.scheme ?: return false
    val serverScheme = server.scheme ?: return false
    if (!pageScheme.equals(serverScheme, ignoreCase = true)) {
        return false
    }
    val pageHost = page.host ?: return false
    val serverHost = server.host ?: return false
    if (!pageHost.equals(serverHost, ignoreCase = true)) {
        return false
    }
    return effectivePort(page) == effectivePort(server)
}

private fun effectivePort(uri: URI): Int {
    if (uri.port != -1) {
        return uri.port
    }
    return when (uri.scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSignInDialog(
    serverUrl: String,
    state: ServerSignInState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onServerPageFinished: () -> Unit
) {
    val loginUrl = remember(serverUrl) { buildServerLoginUrl(serverUrl) }
    var attempt by remember { mutableStateOf(0) }
    var isLoading by remember(loginUrl, attempt) { mutableStateOf(true) }
    var loadError by remember(loginUrl, attempt) { mutableStateOf<String?>(null) }
    val currentOnServerPageFinished by rememberUpdatedState(onServerPageFinished)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                BookOrbitTopBar(
                    title = "Server sign-in",
                    showBrand = false,
                    actions = { TextButton(onClick = onClose) { Text("Close") } }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (loginUrl == null) {
                    OrbitMessage(
                        "The server address is not configured correctly.",
                        modifier = Modifier.padding(16.dp),
                        tone = OrbitMessageTone.ERROR
                    )
                } else {
                    key(loginUrl, attempt) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            if (isSameOrigin(url, serverUrl)) {
                                                CookieManager.getInstance().flush()
                                                currentOnServerPageFinished()
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request.isForMainFrame) {
                                                isLoading = false
                                                loadError = "The sign-in page could not be loaded. Check your connection and try again."
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView,
                                            handler: SslErrorHandler,
                                            error: SslError
                                        ) {
                                            handler.cancel()
                                            isLoading = false
                                            loadError = "The server's TLS certificate could not be validated."
                                        }
                                    }
                                    loadUrl(loginUrl)
                                }
                            }
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    val message = loadError ?: state.error
                    if (message != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OrbitMessage(message, tone = OrbitMessageTone.ERROR)
                            Button(
                                onClick = {
                                    loadError = null
                                    attempt += 1
                                    onRetry()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }
                    } else if (state.isVerifying) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                            Text("Checking your sign-in with the server…")
                        }
                    }
                }
            }
        }
    }
}
