package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class NetworkSecurityConfigInstrumentedTest {
    @Test
    fun baseConfigPermitsCleartextAndTrustsSystemAndUserAnchors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = context.resources.getXml(R.xml.network_security_config)

        var inBaseConfig = false
        var cleartextTrafficPermitted: String? = null
        val trustAnchorSources = mutableListOf<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "base-config" -> {
                        inBaseConfig = true
                        cleartextTrafficPermitted = parser.getAttributeValue(null, "cleartextTrafficPermitted")
                    }
                    "certificates" -> if (inBaseConfig) {
                        parser.getAttributeValue(null, "src")?.let { trustAnchorSources.add(it) }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG && parser.name == "base-config") {
                inBaseConfig = false
            }
            eventType = parser.next()
        }
        parser.close()

        assertEquals("true", cleartextTrafficPermitted)
        assertTrue("expected system trust anchor in base-config", trustAnchorSources.contains("system"))
        assertTrue("expected user trust anchor in base-config", trustAnchorSources.contains("user"))
    }
}
