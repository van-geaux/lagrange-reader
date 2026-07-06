package com.bookorbit.android

import android.content.Context

class AppGraph(context: Context) {
    private val repository = BookOrbitRepository(context.applicationContext)
    val coordinator = AppCoordinator(repository)
}
