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
        // It is deferred past the first frames so startup composition, the
        // initial Room read and the first draw are not competing with service
        // creation for the main thread. Commands issued earlier are queued by
        // PlayerController and replayed once the connection is ready, so a tap
        // during this window still starts playback.
        window.decorView.postDelayed(
            { playerController.connect() },
            PLAYBACK_CONNECT_DELAY_MS,
        )
    }

    private companion object {
        /** Delay before binding the playback service after the first frame. */
        const val PLAYBACK_CONNECT_DELAY_MS = 250L
    }
}
