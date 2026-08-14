package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSetupScreen(
    initialServerUrl: String,
    message: String?,
    onContinue: (String) -> Unit
) {
    var server by remember(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BookOrbitTopBar(title = "Connect") },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OrbitEyebrow("Private reader")
                Text("Your library, in orbit.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Connect securely to your BookOrbit server. Your library stays on your server; this app is your reading window.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!message.isNullOrBlank()) {
                    OrbitMessage(message, tone = OrbitMessageTone.ERROR)
                }
                OutlinedTextField(
                    value = server,
                    onValueChange = {
                        server = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "BookOrbit server URL" },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://books.example.com") },
                    singleLine = true
                )
                error?.let {
                    OrbitMessage(it, tone = OrbitMessageTone.ERROR)
                }
                Button(
                    onClick = {
                        val normalized = normalizeServerUrl(server)
                        if (normalized == null) {
                            error = invalidServerUrlMessage()
                        } else {
                            onContinue(normalized)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("Continue")
                }
                if (server.isNotBlank() && !message.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onContinue(server) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginScreen(
    serverUrl: String,
    message: String?,
    isSubmitting: Boolean,
    onChangeServer: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onOpenServerSignIn: () -> Unit
) {
    var username by remember(serverUrl) { mutableStateOf("") }
    var password by remember(serverUrl) { mutableStateOf("") }
    var passwordVisible by remember(serverUrl) { mutableStateOf(false) }
    var validationMessage by remember(serverUrl) { mutableStateOf<String?>(null) }
    var showServerSignInInfo by remember(serverUrl) { mutableStateOf(false) }
    val submit = {
        when {
            username.isBlank() -> validationMessage = "Enter your username."
            password.isBlank() -> validationMessage = "Enter your password."
            else -> {
                validationMessage = null
                onSubmit(username.trim(), password)
            }
        }
    }
    Scaffold(
        topBar = {
            BookOrbitTopBar(
                title = "Sign in",
                actions = { TextButton(onClick = onChangeServer) { Text("Change server") } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (!message.isNullOrBlank()) {
                OrbitMessage(
                    text = message,
                    modifier = Modifier.padding(bottom = 12.dp),
                    tone = OrbitMessageTone.ERROR
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lagrange",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "a BookOrbit reader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    OrbitEyebrow("BookOrbit server")
                    Text(serverUrl, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            validationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "BookOrbit username" },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "BookOrbit password" },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isSubmitting
                            ) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() })
                    )
                    validationMessage?.let {
                        OrbitMessage(it, tone = OrbitMessageTone.ERROR)
                    }
                    Button(
                        onClick = submit,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "BookOrbit controls which server sign-in options are available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenServerSignIn,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                        ) {
                            Text("Open server sign-in")
                        }
                        TextButton(onClick = { showServerSignInInfo = true }) {
                            Text("Why?")
                        }
                    }
                }
            }
        }
    }
    if (showServerSignInInfo) {
        AlertDialog(
            onDismissRequest = { showServerSignInInfo = false },
            confirmButton = {
                TextButton(onClick = { showServerSignInInfo = false }) { Text("Got it") }
            },
            title = { Text("About server sign-in") },
            text = {
                Text(
                    "BookOrbit controls the available server sign-in options. " +
                        "Open server sign-in uses the server's own sign-in page and whichever " +
                        "local or OIDC methods it is configured with. It runs in an embedded " +
                        "browser inside the app, and some identity providers may block sign-in " +
                        "from an embedded browser."
                )
            }
        )
    }
}
