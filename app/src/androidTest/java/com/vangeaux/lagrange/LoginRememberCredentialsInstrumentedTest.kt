package com.vangeaux.lagrange

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginRememberCredentialsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedCredentialsPrefillLoginAndCheckboxControlsRememberFlag() {
        var submitted: Triple<String, String, Boolean>? = null
        var rememberChanged: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    serverUrl = "https://books.example.test",
                    providerId = PROVIDER_BOOKORBIT,
                    serverHostedSignInAvailable = false,
                    configuredServerProfiles = emptyList(),
                    message = null,
                    isSubmitting = false,
                    savedUsername = "reader",
                    savedPassword = "secret",
                    rememberCredentials = true,
                    onRememberCredentialsChange = { rememberChanged = it },
                    onSwitchServer = {},
                    onNewServer = {},
                    onSubmit = { username, password, remember ->
                        submitted = Triple(username, password, remember)
                    },
                    onOpenOidcSignIn = {},
                    onOpenServerSignIn = {}
                )
            }
        }

        composeRule.onNodeWithTag("login-remember-password").assertIsOn()
        composeRule.onNodeWithTag("login-remember-password").performClick()
        composeRule.onNodeWithTag("login-remember-password").assertIsOff()
        assertEquals(false, rememberChanged)
        composeRule.onNodeWithTag("login-submit").performClick()

        assertEquals(Triple("reader", "secret", false), submitted)
    }
}
