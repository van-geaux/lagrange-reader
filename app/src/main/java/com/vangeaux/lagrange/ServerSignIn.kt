package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OidcSignInDialog(
    serverUrl: String,
    state: OidcSignInState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onProviderSelected: (BookOrbitOidcProvider) -> Unit,
    onCallback: (String) -> Unit
) {
    val transaction = state.transaction
    val currentOnCallback by rememberUpdatedState(onCallback)
    var isLoading by remember(transaction?.authorizationUrl) { mutableStateOf(transaction != null) }
    var loadError by remember(transaction?.authorizationUrl) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                BookOrbitTopBar(
                    title = "SSO sign-in",
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
                if (transaction == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            state.isExchanging -> {
                                CircularProgressIndicator()
                                Text("Completing sign-in…")
                            }
                            state.isLoading -> {
                                CircularProgressIndicator()
                                Text("Loading available sign-in providers…")
                            }
                            state.providers.isNotEmpty() -> {
                                Text("Choose a sign-in provider.")
                                state.providers.forEach { provider ->
                                    Button(
                                        onClick = { onProviderSelected(provider) },
                                        enabled = !state.isExchanging,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Sign in with ${provider.label}")
                                    }
                                }
                            }
                            else -> {
                                OrbitMessage(
                                    state.error ?: "No SSO provider is enabled on this server.",
                                    tone = OrbitMessageTone.ERROR
                                )
                                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val container = FrameLayout(context)
                            var callbackHandled = false
                            lateinit var chromeClient: WebChromeClient

                            fun interceptCallback(url: String): Boolean {
                                if (callbackHandled) return true
                                if (!BookOrbitOidc.isExactCallbackUrl(url, transaction.redirectUri)) {
                                    return false
                                }
                                callbackHandled = true
                                currentOnCallback(url)
                                return true
                            }

                            fun createWebView(): WebView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(true)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webChromeClient = chromeClient
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean = interceptCallback(request.url.toString())

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        if (interceptCallback(url)) view.stopLoading()
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        if (isSameOrigin(url, serverUrl)) {
                                            CookieManager.getInstance().flush()
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
                                            loadError = "The SSO sign-in page could not be loaded. Check your connection and try again."
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
                            }

                            chromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message
                                ): Boolean {
                                    val popup = createWebView()
                                    container.addView(
                                        popup,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    val transport = resultMsg.obj as WebView.WebViewTransport
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(window: WebView) {
                                    container.removeView(window)
                                    window.destroy()
                                }
                            }

                            val mainWebView = createWebView()
                            container.addView(
                                mainWebView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            mainWebView.loadUrl(transaction.authorizationUrl)
                            container
                        }
                    )
                    if (isLoading || state.isExchanging) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            if (state.isExchanging) {
                                Text("Completing sign-in…", modifier = Modifier.padding(top = 8.dp))
                            }
                        }
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
                            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
