package com.example.musicplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point that bootstraps Hilt dependency injection.
 */
@HiltAndroidApp
class MusicPlayerApp : Application()
