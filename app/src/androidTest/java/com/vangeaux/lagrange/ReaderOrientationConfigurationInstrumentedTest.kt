package com.vangeaux.lagrange

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityRecreationPolicyInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requiredReaderChanges =
        ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE

    @Test
    fun appAndReaderActivitiesUseSavedInstanceStateRecreation() {
        listOf(
            MainActivity::class.java,
            ReadiumEpubReaderActivity::class.java,
            ReadiumPdfReaderActivity::class.java,
            ReadiumComicReaderActivity::class.java
        ).forEach { activityClass ->
            val activityInfo = context.packageManager.getActivityInfo(
                ComponentName(context, activityClass),
                0
            )

            assertEquals(
                "$activityClass must use saved-instance restoration for size and orientation changes",
                0,
                activityInfo.configChanges and requiredReaderChanges
            )
        }
    }
}
