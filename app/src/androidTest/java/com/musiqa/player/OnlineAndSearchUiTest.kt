package com.musiqa.player

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musiqa.player.ui.components.MusicPreferenceOnboardingDialog
import com.musiqa.player.ui.screens.online.OnlineScreen
import com.musiqa.player.ui.theme.MusiqaTheme
import com.musiqa.player.online.DemoOnlineMusicRepository
import com.musiqa.player.online.OnlineMusicViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineAndSearchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("musiqa_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun onboardingDialog_supportsGlobalArtistsAndCustomArtistAddition() {
        var savedLanguages = emptySet<String>()
        var savedGenres = emptySet<String>()
        var savedArtists = emptySet<String>()

        composeRule.setContent {
            MusiqaTheme(darkTheme = true) {
                MusicPreferenceOnboardingDialog(
                    isFirstRun = true,
                    onDismiss = {},
                    onSave = { langs, genres, artists ->
                        savedLanguages = langs
                        savedGenres = genres
                        savedArtists = artists
                    }
                )
            }
        }

        // Verify expanded languages & skip button
        composeRule.onNodeWithText("English (US)").assertIsDisplayed()
        composeRule.onNodeWithText("Skip for now (Surprise me)").assertIsDisplayed()

        // Scroll to custom artist input and add custom artist
        composeRule.onNodeWithText("Filter or add any singer (e.g. Weeknd, Talha Anjum)")
            .performScrollTo()
            .performTextInput("Talha Anjum")
        composeRule.onNodeWithContentDescription("Add artist").performClick()

        composeRule.onNodeWithText("Your Added Artists").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Talha Anjum").performScrollTo().assertIsDisplayed()

        // Save
        composeRule.onNodeWithText("Start Listening").performClick()
        assertTrue("Custom artist should be saved", "Talha Anjum" in savedArtists)
        assertTrue("Languages should not be empty", savedLanguages.isNotEmpty())
    }

    @Test
    fun onlineSearch_cursorStaysAtEndOnAppend_andNoEmptyFlicker() {
        val delegate = DemoOnlineMusicRepository()
        val asyncRepo = object : com.musiqa.player.online.OnlineMusicRepository by delegate {
            override suspend fun search(query: String): List<com.musiqa.player.online.OnlineTrack> {
                kotlinx.coroutines.delay(400)
                return delegate.search(query)
            }
        }
        composeRule.setContent {
            val vm = androidx.compose.runtime.remember {
                OnlineMusicViewModel(app, asyncRepo).apply {
                    updatePreferences(setOf("English (US)", "Hindi"), setOf("Pop"), setOf("The Weeknd"))
                }
            }
            MusiqaTheme(darkTheme = true) {
                OnlineScreen(viewModel = vm)
            }
        }

        // Open Search
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Search online music").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("Search online music").onFirst().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Search online music").fetchSemanticsNodes().isNotEmpty()
        }

        val searchBox = composeRule.onNode(hasSetTextAction())
        searchBox.performTextInput("neon")

        // Verify NO premature "No suggestions found" flicker while debouncing/searching
        assertTrue(
            "Should not flash 'No suggestions found' while searching",
            composeRule.onAllNodesWithText("No suggestions found").fetchSemanticsNodes().isEmpty()
        )

        // Wait for suggestion with ↖ ("Insert query") button
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Insert query").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap ↖ to append suggestion, then type "live" and verify cursor was at the END
        composeRule.onAllNodesWithContentDescription("Insert query").onFirst().performClick()
        composeRule.waitForIdle()
        searchBox.performTextInput("live")

        val nodeText = searchBox.fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.EditableText) { AnnotatedString("") }.text
        assertTrue(
            "Expected search text to end with 'live' (cursor at end), but was: '$nodeText'",
            nodeText.endsWith("live")
        )

        // Submit committed search & verify no premature "No matches found"
        searchBox.performImeAction()
        assertTrue(
            "Should not flash 'No matches found' immediately on submit",
            composeRule.onAllNodesWithText("No matches found").fetchSemanticsNodes().isEmpty()
        )

        // Clear search and test a matching query ("neon")
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        searchBox.performTextInput("neon")
        searchBox.performImeAction()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Tracks").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Neon Horizon").assertIsDisplayed()
    }
}
