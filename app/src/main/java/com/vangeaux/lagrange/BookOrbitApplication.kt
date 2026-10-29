package com.vangeaux.lagrange

import android.app.Application
import android.content.Context
import android.webkit.CookieManager

class BookOrbitApplication : Application() {
    lateinit var audioPlaybackController: ReadiumAudioPlaybackController
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
        audioPlaybackController = ReadiumAudioPlaybackController(this)
        PortableDownloadCleanup.schedule(this)
    }
}
