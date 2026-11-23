package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseApkVerifierInstrumentedTest {
    @Test
    fun parsesPackageVersionAndSignerFromAnApkArchive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installedApk = File(context.applicationInfo.sourceDir)

        val metadata = ReleaseApkVerifier.inspectArchive(context, installedApk)

        assertNotNull(metadata)
        assertEquals(context.packageName, metadata?.packageName)
        assertEquals(BuildConfig.VERSION_NAME, metadata?.versionName)
        val signerFingerprint = metadata?.signerFingerprints?.singleOrNull()
        assertNotNull(signerFingerprint)
        assertTrue(signerFingerprint?.matches(Regex("^[0-9a-fA-F]{64}$")) == true)
        assertEquals(
            ReleaseApkVerificationStatus.MISMATCH,
            ReleaseApkVerifier.evaluate(
                metadata = metadata,
                expectedPackageName = context.packageName,
                expectedVersionName = BuildConfig.VERSION_NAME,
                expectedSignerFingerprint = ReleaseSignerCompatibility.expectedReleaseFingerprint(context)
            )
        )
    }
}
