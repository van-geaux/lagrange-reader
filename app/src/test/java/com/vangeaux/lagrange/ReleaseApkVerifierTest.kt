package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseApkVerifierTest {
    private val expectedSigner = "da44867c9bfb83f8f8a1fcc1cd8d3aeac2ca515f4dd0c95b58cf5369f6f14681"

    @Test
    fun `matching package version and signer verifies`() {
        assertEquals(
            ReleaseApkVerificationStatus.MATCH,
            ReleaseApkVerifier.evaluate(
                metadata = ReleaseApkMetadata(
                    packageName = "com.vangeaux.lagrange",
                    versionName = "2.1.4",
                    signerFingerprints = listOf(expectedSigner)
                ),
                expectedPackageName = "com.vangeaux.lagrange",
                expectedVersionName = "2.1.4",
                expectedSignerFingerprint = expectedSigner
            )
        )
    }

    @Test
    fun `wrong package or release version is rejected`() {
        val valid = ReleaseApkMetadata(
            packageName = "com.vangeaux.lagrange",
            versionName = "2.1.4",
            signerFingerprints = listOf(expectedSigner)
        )

        assertEquals(
            ReleaseApkVerificationStatus.MISMATCH,
            ReleaseApkVerifier.evaluate(
                metadata = valid.copy(packageName = "com.example.other"),
                expectedPackageName = "com.vangeaux.lagrange",
                expectedVersionName = "2.1.4",
                expectedSignerFingerprint = expectedSigner
            )
        )
        assertEquals(
            ReleaseApkVerificationStatus.MISMATCH,
            ReleaseApkVerifier.evaluate(
                metadata = valid.copy(versionName = "2.1.3"),
                expectedPackageName = "com.vangeaux.lagrange",
                expectedVersionName = "2.1.4",
                expectedSignerFingerprint = expectedSigner
            )
        )
    }

    @Test
    fun `different target signer is rejected`() {
        assertEquals(
            ReleaseApkVerificationStatus.MISMATCH,
            ReleaseApkVerifier.evaluate(
                metadata = ReleaseApkMetadata(
                    packageName = "com.vangeaux.lagrange",
                    versionName = "2.1.4",
                    signerFingerprints = listOf("f277b8b568569986bfb4f2fbd7a35a09eddfa10349568c5f0a8264ccb409b4c0")
                ),
                expectedPackageName = "com.vangeaux.lagrange",
                expectedVersionName = "2.1.4",
                expectedSignerFingerprint = expectedSigner
            )
        )
    }

    @Test
    fun `missing or multiple signer metadata fails closed`() {
        val valid = ReleaseApkMetadata(
            packageName = "com.vangeaux.lagrange",
            versionName = "2.1.4",
            signerFingerprints = listOf(expectedSigner)
        )
        val arguments = listOf(
            Triple(null, "2.1.4", listOf(expectedSigner)),
            Triple("com.vangeaux.lagrange", null, listOf(expectedSigner)),
            Triple("com.vangeaux.lagrange", "2.1.4", null),
            Triple("com.vangeaux.lagrange", "2.1.4", emptyList()),
            Triple("com.vangeaux.lagrange", "2.1.4", listOf(expectedSigner, expectedSigner))
        )

        arguments.forEach { (packageName, versionName, signers) ->
            assertEquals(
                ReleaseApkVerificationStatus.UNKNOWN,
                ReleaseApkVerifier.evaluate(
                    metadata = valid.copy(
                        packageName = packageName,
                        versionName = versionName,
                        signerFingerprints = signers
                    ),
                    expectedPackageName = "com.vangeaux.lagrange",
                    expectedVersionName = "2.1.4",
                    expectedSignerFingerprint = expectedSigner
                )
            )
        }
    }

    @Test
    fun `malformed or missing trusted fingerprint fails closed`() {
        val metadata = ReleaseApkMetadata(
            packageName = "com.vangeaux.lagrange",
            versionName = "2.1.4",
            signerFingerprints = listOf(expectedSigner)
        )

        listOf(null, "", "not-a-fingerprint", "a".repeat(63), "g".repeat(64)).forEach { pin ->
            assertEquals(
                ReleaseApkVerificationStatus.UNKNOWN,
                ReleaseApkVerifier.evaluate(
                    metadata = metadata,
                    expectedPackageName = "com.vangeaux.lagrange",
                    expectedVersionName = "2.1.4",
                    expectedSignerFingerprint = pin
                )
            )
        }
    }
}
