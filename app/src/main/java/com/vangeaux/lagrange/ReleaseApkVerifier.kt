package com.vangeaux.lagrange

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal data class ReleaseApkMetadata(
    val packageName: String?,
    val versionName: String?,
    val signerFingerprints: List<String>?
)

internal enum class ReleaseApkVerificationStatus {
    MATCH,
    MISMATCH,
    UNKNOWN
}

internal class ReleaseApkVerificationException(
    val status: ReleaseApkVerificationStatus
) : Exception()

internal object ReleaseApkVerifier {
    private val fingerprintPattern = Regex("^[0-9a-fA-F]{64}$")

    fun evaluate(
        metadata: ReleaseApkMetadata?,
        expectedPackageName: String,
        expectedVersionName: String,
        expectedSignerFingerprint: String?
    ): ReleaseApkVerificationStatus {
        val archive = metadata ?: return ReleaseApkVerificationStatus.UNKNOWN
        if (expectedPackageName.isBlank() || expectedVersionName.isBlank() ||
            expectedSignerFingerprint?.let(fingerprintPattern::matches) != true
        ) {
            return ReleaseApkVerificationStatus.UNKNOWN
        }
        if (archive.packageName.isNullOrBlank() || archive.versionName.isNullOrBlank()) {
            return ReleaseApkVerificationStatus.UNKNOWN
        }
        if (archive.packageName != expectedPackageName || archive.versionName != expectedVersionName) {
            return ReleaseApkVerificationStatus.MISMATCH
        }
        val signerFingerprint = archive.signerFingerprints
            ?.singleOrNull()
            ?.takeIf(fingerprintPattern::matches)
            ?: return ReleaseApkVerificationStatus.UNKNOWN
        return if (signerFingerprint.equals(expectedSignerFingerprint, ignoreCase = true)) {
            ReleaseApkVerificationStatus.MATCH
        } else {
            ReleaseApkVerificationStatus.MISMATCH
        }
    }

    fun inspectArchive(context: Context, apkFile: File): ReleaseApkMetadata? {
        return try {
            if (!apkFile.isFile || apkFile.length() <= 0L) return null
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                packageInfoFlags()
            ) ?: return null
            ReleaseApkMetadata(
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName,
                signerFingerprints = archiveSignerFingerprints(packageInfo)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        update: ReleaseUpdate
    ): ReleaseApkVerificationStatus = evaluate(
        metadata = inspectArchive(context, apkFile),
        expectedPackageName = context.packageName,
        expectedVersionName = update.versionName,
        expectedSignerFingerprint = ReleaseSignerCompatibility.expectedReleaseFingerprint(context)
    )

    @Suppress("DEPRECATION")
    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    @Suppress("DEPRECATION")
    private fun archiveSignerFingerprints(packageInfo: PackageInfo): List<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo
                ?.takeUnless { it.hasMultipleSigners() }
                ?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        return signatures?.singleOrNull()?.let { signature ->
            listOf(fingerprint(signature.toByteArray()))
        }
    }

    private fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
