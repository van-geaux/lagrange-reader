package com.vangeaux.lagrange

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseApkCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `cleanup deletes installed versions and older files but keeps newer update candidates`() {
        val cacheDir = temporaryFolder.newFolder("release-apk-cache")
        val updateDir = File(cacheDir, "update-apks").apply { mkdirs() }
        val obsoleteApk = File(updateDir, "Lagrange-2.1.2.apk").apply { writeText("old") }
        val installedApk = File(updateDir, "Lagrange-2.1.3.apk").apply { writeText("current") }
        val obsoletePartial = File(updateDir, "Lagrange-2.1.2.apk.part").apply { writeText("partial") }
        val pendingApk = File(updateDir, "Lagrange-2.1.4.apk").apply { writeText("pending") }
        val pendingPartial = File(updateDir, "Lagrange-2.1.4.apk.part").apply { writeText("pending-partial") }
        val unrelatedApk = File(updateDir, "other-app.apk").apply { writeText("unrelated") }
        val backupFile = File(updateDir, "Lagrange-2.1.2.apk.bak").apply { writeText("backup") }

        ReleaseApkCache.cleanupInstalledOrOlder(cacheDir, "2.1.3")

        assertFalse(obsoleteApk.exists())
        assertFalse(installedApk.exists())
        assertFalse(obsoletePartial.exists())
        assertTrue(pendingApk.exists())
        assertTrue(pendingPartial.exists())
        assertTrue(unrelatedApk.exists())
        assertTrue(backupFile.exists())
    }

    @Test
    fun `partial download is deleted when the download action fails`() {
        val partial = File(temporaryFolder.newFolder("release-apk-partial"), "Lagrange-2.1.4.apk.part")
        partial.writeText("incomplete")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            ReleaseApkCache.deletePartialOnFailure(partial) {
                throw IllegalStateException("network failed")
            }
        }

        assertFalse(partial.exists())
    }

    @Test
    fun `cleanup leaves candidates untouched when installed version is not parseable`() {
        val cacheDir = temporaryFolder.newFolder("release-apk-cache-invalid-version")
        val updateDir = File(cacheDir, "update-apks").apply { mkdirs() }
        val candidate = File(updateDir, "Lagrange-2.1.2.apk").apply { writeText("candidate") }

        ReleaseApkCache.cleanupInstalledOrOlder(cacheDir, "2.1")

        assertTrue(candidate.exists())
    }
}
