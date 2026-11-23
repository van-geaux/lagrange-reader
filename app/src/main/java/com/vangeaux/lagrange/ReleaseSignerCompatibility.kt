package com.vangeaux.lagrange

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

enum class ReleaseSignerStatus {
    MATCH,
    MISMATCH,
    UNKNOWN
}

internal object ReleaseSignerCompatibility {
    private const val RELEASE_SIGNER_FINGERPRINT_ASSET = "release-signer-sha256.txt"
    private val fingerprintPattern = Regex("^[0-9a-fA-F]{64}$")

    fun evaluate(installedFingerprint: String?, expectedFingerprint: String?): ReleaseSignerStatus {
        val installed = installedFingerprint?.takeIf(fingerprintPattern::matches)
            ?: return ReleaseSignerStatus.UNKNOWN
        val expected = expectedFingerprint?.takeIf(fingerprintPattern::matches)
            ?: return ReleaseSignerStatus.UNKNOWN
        return if (installed.equals(expected, ignoreCase = true)) {
            ReleaseSignerStatus.MATCH
        } else {
            ReleaseSignerStatus.MISMATCH
        }
    }

    fun currentInstalledStatus(context: Context): ReleaseSignerStatus {
        val expectedFingerprint = expectedReleaseFingerprint(context)
        val installedFingerprint = try {
            installedSignerFingerprint(context.packageManager, context.packageName)
        } catch (_: Exception) {
            null
        }
        return evaluate(installedFingerprint, expectedFingerprint)
    }

    fun expectedReleaseFingerprint(context: Context): String? = try {
        context.assets.open(RELEASE_SIGNER_FINGERPRINT_ASSET).bufferedReader().use {
            it.readText().trim()
        }
    } catch (_: Exception) {
        null
    }

    fun routeUpdateAction(
        status: ReleaseSignerStatus,
        onDownload: () -> Unit,
        onManualSwitch: () -> Unit
    ) {
        if (status == ReleaseSignerStatus.MATCH) onDownload() else onManualSwitch()
    }

    @Suppress("DEPRECATION")
    private fun installedSignerFingerprint(
        packageManager: PackageManager,
        packageName: String
    ): String? {
        val packageInfo = packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        )
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo
                ?.takeUnless { it.hasMultipleSigners() }
                ?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        val certificate = signatures?.singleOrNull()?.toByteArray() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate)
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
