package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSetupScreen(
    initialServerUrl: String,
    initialServerName: String,
    initialProviderId: String,
    configuredServerProfiles: List<ServerProfile>,
    message: String?,
    onSwitchServer: (String) -> Unit,
    onContinue: (String, String, String) -> Unit
) {
    var server by remember(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var serverName by remember(initialServerUrl, initialServerName) { mutableStateOf(initialServerName) }
    var providerId by remember(initialServerUrl, initialProviderId) { mutableStateOf(initialProviderId) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    val serverDescription = if (providerId.isNotBlank()) {
        stringResource(R.string.setup_your_provider_server, serverProviderDisplayName(providerId))
    } else {
        stringResource(R.string.setup_your_server)
    }
    val providerButtonLabel = providerId.takeIf { it.isNotBlank() }
        ?.let(::serverProviderDisplayName)
        ?: stringResource(R.string.setup_select_server_type)
    val serverUrlDescription = stringResource(
        R.string.setup_server_url_description,
        serverProviderDisplayName(providerId)
    )
    val submit = {
        if (providerId.isBlank()) {
            errorRes = R.string.setup_select_server_type_error
        } else {
            val normalized = normalizeServerUrl(server)
            if (normalized == null) {
                errorRes = R.string.setup_invalid_server_url
            } else {
                onContinue(normalized, serverName.trim(), providerId)
            }
        }
    }

    Scaffold(
        topBar = { BookOrbitTopBar(title = stringResource(R.string.setup_title_connect)) },
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
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OrbitEyebrow(stringResource(R.string.setup_eyebrow_private_reader))
                Text(
                    stringResource(R.string.setup_tagline_your_library),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    stringResource(R.string.setup_intro, serverDescription),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!message.isNullOrBlank()) {
                    OrbitMessage(message, tone = OrbitMessageTone.ERROR)
                }
                OutlinedTextField(
                    value = serverName,
                    onValueChange = {
                        serverName = it
                        errorRes = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.setup_server_name_label)) },
                    placeholder = { Text(stringResource(R.string.setup_server_name_placeholder)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = {
                        server = it
                        errorRes = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = serverUrlDescription },
                    label = { Text(stringResource(R.string.setup_server_url_label)) },
                    placeholder = { Text(stringResource(R.string.setup_server_url_placeholder)) },
                    singleLine = true
                )
                Text(stringResource(R.string.setup_server_type_title), style = MaterialTheme.typography.titleMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { providerMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            providerButtonLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(PROVIDER_BOOKORBIT, PROVIDER_KOMGA, PROVIDER_KAVITA).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(serverProviderDisplayName(option)) },
                                onClick = {
                                    providerId = option
                                    providerMenuExpanded = false
                                    errorRes = null
                                }
                            )
                        }
                    }
                }
                errorRes?.let { res ->
                    if (providerId.isBlank() && res == R.string.setup_select_server_type_error) {
                        Text(
                            text = stringResource(res),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (res != R.string.setup_select_server_type_error) {
                        OrbitMessage(stringResource(res), tone = OrbitMessageTone.ERROR)
                    }
                }
                Button(
                    onClick = submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.common_continue))
                }
                if (server.isNotBlank() && !message.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                if (configuredServerProfiles.isNotEmpty()) {
                    Text(stringResource(R.string.setup_configured_servers), style = MaterialTheme.typography.titleMedium)
                    configuredServerProfiles.forEach { profile ->
                        ConfiguredServerCard(
                            profile = profile,
                            isCurrent = serverUrlsMatch(profile.serverUrl, initialServerUrl),
                            onClick = { onSwitchServer(profile.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConfiguredServerCard(
    profile: ServerProfile,
    isCurrent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        profile.displayName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            profile.providerId,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    profile.serverUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        onRemove?.let { remove ->
            TextButton(onClick = remove) {
                Text(if (isCurrent) stringResource(R.string.setup_current) else stringResource(R.string.setup_remove))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginScreen(
    serverUrl: String,
    providerId: String,
    serverHostedSignInAvailable: Boolean,
    configuredServerProfiles: List<ServerProfile>,
    message: String?,
    isSubmitting: Boolean,
    onSwitchServer: (String) -> Unit,
    onNewServer: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onOpenOidcSignIn: () -> Unit,
    onOpenServerSignIn: () -> Unit
) {
    var username by remember(serverUrl) { mutableStateOf("") }
    var password by remember(serverUrl) { mutableStateOf("") }
    var passwordVisible by remember(serverUrl, providerId) { mutableStateOf(false) }
    var validationMessageRes by remember(serverUrl) { mutableStateOf<Int?>(null) }
    var showServerPicker by rememberSaveable(serverUrl) { mutableStateOf(false) }
    val submit = {
        when {
            username.isBlank() -> validationMessageRes = R.string.login_enter_username
            password.isBlank() -> validationMessageRes = R.string.login_enter_password
            else -> {
                validationMessageRes = null
                onSubmit(username.trim(), password)
            }
        }
    }
    val usernameDescription = stringResource(
        R.string.login_username_description,
        serverProviderDisplayName(providerId)
    )
    val passwordDescription = stringResource(
        R.string.login_password_description,
        serverProviderDisplayName(providerId)
    )
    Scaffold(
        topBar = {
            BookOrbitTopBar(
                title = stringResource(R.string.login_title),
                actions = {
                    TextButton(onClick = { showServerPicker = true }) {
                        Text(stringResource(R.string.common_change_server))
                    }
                }
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
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = stringResource(R.string.login_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    OrbitEyebrow(
                        stringResource(R.string.provider_server_label, serverProviderDisplayName(providerId))
                    )
                    Text(serverUrl, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            validationMessageRes = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = usernameDescription },
                        label = { Text(stringResource(R.string.login_username_label)) },
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
                            validationMessageRes = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = passwordDescription },
                        label = { Text(stringResource(R.string.login_password_label)) },
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
                                Text(
                                    stringResource(
                                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password
                                    )
                                )
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() })
                    )
                    validationMessageRes?.let { res ->
                        OrbitMessage(stringResource(res), tone = OrbitMessageTone.ERROR)
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
                            Text(stringResource(R.string.common_sign_in))
                        }
                    }
                    if (serverHostedSignInAvailable) {
                        OutlinedButton(
                            onClick = onOpenServerSignIn,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(stringResource(R.string.login_open_server_sign_in))
                        }
                    } else if (providerId == PROVIDER_BOOKORBIT) {
                        OutlinedButton(
                            onClick = onOpenOidcSignIn,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            Text(stringResource(R.string.login_sign_in_with_sso))
                        }
                    }
                }
            }
        }
    }
    if (showServerPicker) {
        AlertDialog(
            onDismissRequest = { showServerPicker = false },
            title = { Text(stringResource(R.string.common_change_server)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.login_choose_server))
                    configuredServerProfiles.forEach { profile ->
                        val isCurrent = serverUrlsMatch(profile.serverUrl, serverUrl)
                        ConfiguredServerCard(
                            profile = profile,
                            isCurrent = isCurrent,
                            enabled = !isCurrent,
                            onClick = {
                                showServerPicker = false
                                onSwitchServer(profile.id)
                            }
                        )
                    }
                    Button(
                        onClick = {
                            showServerPicker = false
                            onNewServer()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.login_new_server)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
