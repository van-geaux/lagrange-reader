package com.bookorbit.android

import android.app.Application
import android.webkit.CookieManager

class BookOrbitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
    }
}
