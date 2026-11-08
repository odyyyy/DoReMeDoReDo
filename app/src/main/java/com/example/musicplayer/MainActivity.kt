package com.example.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.musicplayer.navigation.AppNavigation
import com.example.musicplayer.player.PlayerController
import com.example.musicplayer.ui.theme.DoReMeDoReDoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoReMeDoReDoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AppNavigation()
                }
            }
        }

        // Connecting binds the playback service, which creates the single
        // ExoPlayer, its renderers and the MediaSession on the main thread.
        // Deferring it keeps that work out of the synchronous onCreate/setContent
        // startup path; commands issued earlier are queued by PlayerController
        // and replayed once the connection is ready.
        window.decorView.post { playerController.connect() }
    }
}
