package com.bookorbit.android

import android.app.Application
import android.webkit.CookieManager

class BookOrbitApplication : Application() {
    lateinit var audioPlaybackController: ReadiumAudioPlaybackController
        private set

    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
        audioPlaybackController = ReadiumAudioPlaybackController(this)
    }
}
