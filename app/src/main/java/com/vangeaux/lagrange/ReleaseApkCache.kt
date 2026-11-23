package com.vangeaux.lagrange

import java.io.File

internal object ReleaseApkCache {
    private val versionedApkFile = Regex("^Lagrange-(\\d+\\.\\d+\\.\\d+)\\.apk(\\.part)?$")

    fun cleanupInstalledOrOlder(cacheDir: File, installedVersion: String) {
        val installed = parseVersion(installedVersion) ?: return
        val updateDirectory = File(cacheDir, "update-apks")
        updateDirectory.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val version = versionedApkFile.matchEntire(file.name)
                ?.groupValues?.get(1)
                ?.let(::parseVersion)
                ?: return@forEach
            if (version <= installed) file.delete()
        }
    }

    inline fun <T> deletePartialOnFailure(partial: File, action: () -> T): T =
        try {
            action()
        } catch (error: Exception) {
            partial.delete()
            throw error
        }

    private fun parseVersion(value: String): Version? {
        val parts = value.split('.')
        if (parts.size != 3) return null
        return Version(
            major = parts[0].toIntOrNull() ?: return null,
            minor = parts[1].toIntOrNull() ?: return null,
            patch = parts[2].toIntOrNull() ?: return null
        )
    }

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
    }
}
