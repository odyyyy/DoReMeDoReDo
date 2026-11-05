package com.example.musicplayer.presentation.player

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musicplayer.player.PlaybackRepeatMode
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI checks for the Now Playing layout.
 *
 * The stateless [NowPlayingContent] is driven with literal state, so these tests
 * assert rendering, control wiring, mode state descriptions and seek handling
 * without touching the service. The service-backed playback path is covered by
 * `PlayerControllerInstrumentedTest`.
 */
@RunWith(AndroidJUnit4::class)
class NowPlayingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backCount = 0
    private var queueCount = 0
    private var togglePlayPauseCount = 0
    private var nextCount = 0
    private var previousCount = 0
    private var shuffleCount = 0
    private var repeatCount = 0
    private var seekedTo: Long? = null

    private fun setContent(
        state: PlayerUiState?,
        progress: PlaybackProgress? = progressState(),
    ) {
        composeRule.setContent {
            MusicPlayerTheme {
                NowPlayingContent(
                    state = state,
                    progress = { progress },
                    onBackClicked = { backCount++ },
                    onOpenQueue = { queueCount++ },
                    onTogglePlayPause = { togglePlayPauseCount++ },
                    onNext = { nextCount++ },
                    onPrevious = { previousCount++ },
                    onToggleShuffle = { shuffleCount++ },
                    onCycleRepeatMode = { repeatCount++ },
                    onSeekTo = { seekedTo = it },
                )
            }
        }
    }

    @Test
    fun showsArtworkTitleArtistAlbumAndTimes() {
        setContent(state())

        composeRule.onNodeWithText("S").assertIsDisplayed() // artwork placeholder initial
        composeRule.onNodeWithText("Song").assertIsDisplayed()
        composeRule.onNodeWithText("Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Album").assertIsDisplayed()
        composeRule.onNodeWithText("0:10").assertIsDisplayed() // elapsed
        composeRule.onNodeWithText("3:20").assertIsDisplayed() // total
    }

    @Test
    fun transportAndNavigationControlsReportCommands() {
        setContent(state())

        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithContentDescription("Previous track").performClick()
        composeRule.onNodeWithContentDescription("Shuffle").performClick()
        composeRule.onNodeWithContentDescription("Repeat").performClick()
        composeRule.onNodeWithContentDescription("Queue").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, togglePlayPauseCount)
        assertEquals(1, nextCount)
        assertEquals(1, previousCount)
        assertEquals(1, shuffleCount)
        assertEquals(1, repeatCount)
        assertEquals(1, queueCount)
        assertEquals(1, backCount)
    }

    @Test
    fun playingStateShowsPauseControl() {
        setContent(state(isPlaying = true))

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun shuffleAndRepeatExposeTheirStateToAccessibility() {
        setContent(state(shuffleModeEnabled = true, repeatMode = PlaybackRepeatMode.ONE))

        composeRule.onNodeWithContentDescription("Shuffle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
        composeRule.onNodeWithContentDescription("Repeat")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "One"))
    }

    @Test
    fun repeatAndShuffleReportTheirOffState() {
        setContent(state())

        composeRule.onNodeWithContentDescription("Shuffle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
        composeRule.onNodeWithContentDescription("Repeat")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }

    @Test
    fun nextIsDisabledAtTheEndOfTheQueue() {
        setContent(state(hasNext = false))

        composeRule.onNodeWithContentDescription("Next track").assertIsNotEnabled()
    }

    @Test
    fun seekingReportsTheTargetPosition() {
        setContent(state())

        composeRule.onNodeWithContentDescription("Playback position").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress ->
            setProgress(0.5f)
        }

        val target = seekedTo
        assertNotNull(target)
        // 0.5 of a 200 s track, allowing for float rounding.
        assertTrue("Seeked to $target", target!! in 99_000L..101_000L)
    }

    @Test
    fun seekBarIsDisabledWithoutADuration() {
        setContent(state(), progress = progressState(durationMs = 0L))

        composeRule.onNodeWithContentDescription("Playback position").assertIsNotEnabled()
    }

    @Test
    fun emptyStateWhenNothingIsLoaded() {
        setContent(state = null, progress = null)

        composeRule.onNodeWithText("Nothing is playing").assertIsDisplayed()
    }

    @Test
    fun bufferingErrorAndEndedStatesAreDisplayed() {
        setContent(state(isBuffering = true))
        composeRule.onNodeWithContentDescription("Buffering").assertExists()
        // The buffering caption is gone: loading is shown as a silent indicator.
        composeRule.onNodeWithText("Buffering...").assertDoesNotExist()
    }

    @Test
    fun errorStateIsDisplayed() {
        setContent(state(errorMessage = "The track could not be read."))

        composeRule.onNodeWithText("The track could not be read.").assertIsDisplayed()
    }

    @Test
    fun endedStateIsDisplayed() {
        setContent(state(hasEnded = true))

        composeRule.onNodeWithText("Playback finished").assertIsDisplayed()
    }

    private fun state(
        isPlaying: Boolean = false,
        isBuffering: Boolean = false,
        hasNext: Boolean = true,
        shuffleModeEnabled: Boolean = false,
        repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
        hasEnded: Boolean = false,
        errorMessage: String? = null,
    ) = PlayerUiState(
        trackUri = "content://music/track-1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        artworkUri = null,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        hasNext = hasNext,
        hasPrevious = false,
        shuffleModeEnabled = shuffleModeEnabled,
        repeatMode = repeatMode,
        hasEnded = hasEnded,
        errorMessage = errorMessage,
    )

    private fun progressState(
        positionMs: Long = 10_000L,
        durationMs: Long = 200_000L,
    ) = PlaybackProgress(
        trackUri = "content://music/track-1",
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = false,
    )
}