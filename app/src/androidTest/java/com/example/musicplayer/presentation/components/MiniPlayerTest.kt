package com.example.musicplayer.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musicplayer.presentation.player.PlayerUiState
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI checks for the persistent Mini Player panel.
 *
 * The panel is stateless, so these tests drive it with literal states and
 * assert the rendered content and the commands it reports upwards; the
 * service-backed behaviour is covered by `PlayerControllerInstrumentedTest`.
 */
@RunWith(AndroidJUnit4::class)
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var openNowPlayingCount = 0
    private var togglePlayPauseCount = 0
    private var nextCount = 0

    private fun setContent(state: PlayerUiState) {
        composeRule.setContent {
            MusicPlayerTheme {
                MiniPlayer(
                    state = state,
                    onOpenNowPlaying = { openNowPlayingCount++ },
                    onTogglePlayPause = { togglePlayPauseCount++ },
                    onNext = { nextCount++ },
                )
            }
        }
    }

    @Test
    fun showsTrackTitleArtistAndPlayControl() {
        setContent(state())

        composeRule.onNodeWithText("Song").assertIsDisplayed()
        composeRule.onNodeWithText("Artist").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun playingStateSwapsPlayForPause() {
        setContent(state(isPlaying = true, hasNext = true))

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next track").assertIsDisplayed()
    }

    @Test
    fun controlsReportCommandsToThePlayerOwner() {
        setContent(state())

        composeRule.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, togglePlayPauseCount)
        assertEquals(0, openNowPlayingCount)

        composeRule.onNodeWithContentDescription("Next track").performClick()
        assertEquals(1, nextCount)
        assertEquals(0, openNowPlayingCount)

        // Tapping the panel body (not a control) opens Now Playing.
        composeRule.onNodeWithText("Song").performClick()
        assertEquals(1, openNowPlayingCount)
    }

    @Test
    fun nextControlIsHiddenWhenTheQueueEnds() {
        setContent(state(hasNext = false))

        composeRule.onNodeWithContentDescription("Next track").assertDoesNotExist()
    }

    @Test
    fun unknownArtistIsLabelled() {
        setContent(state(artist = null))

        composeRule.onNodeWithText("Unknown Artist").assertIsDisplayed()
    }

    @Test
    fun bufferingAndErrorStatesAreDisplayed() {
        setContent(state(isBuffering = true, errorMessage = "Playback failed."))

        composeRule.onNodeWithContentDescription("Buffering").assertExists()
        composeRule.onNodeWithText("Playback failed.").assertIsDisplayed()
    }

    private fun state(
        isPlaying: Boolean = false,
        isBuffering: Boolean = false,
        hasNext: Boolean = true,
        artist: String? = "Artist",
        errorMessage: String? = null,
    ) = PlayerUiState(
        trackUri = "content://music/track-1",
        title = "Song",
        artist = artist,
        album = "Album",
        artworkUri = null,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        hasNext = hasNext,
        hasPrevious = false,
        errorMessage = errorMessage,
    )
}