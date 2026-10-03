package com.example.data.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object VideoPlayerState {
    var isFullScreen by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
}

object MukulOttNavState {
    var pendingMovieSlug by mutableStateOf<String?>(null)
    var pendingMovieTitle by mutableStateOf<String?>(null)
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
