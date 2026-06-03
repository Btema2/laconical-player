package com.laconical.player.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.laconical.player.ui.navigation.NavRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class LaconicalBottomNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selected tab shows label`() {
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = {}
            )
        }
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
    }

    @Test
    fun `unselected tabs show no label`() {
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = {}
            )
        }
        composeTestRule.onNodeWithText("Tracks").assertDoesNotExist()
        composeTestRule.onNodeWithText("Artists").assertDoesNotExist()
        composeTestRule.onNodeWithText("Playlists").assertDoesNotExist()
    }

    @Test
    fun `tapping tab fires callback with that route`() {
        var tapped = ""
        composeTestRule.setContent {
            LaconicalBottomNav(
                selectedRoute = NavRoute.ALBUMS,
                onTabSelected = { tapped = it }
            )
        }
        composeTestRule.onNodeWithText("Albums").performClick()
        assertEquals(NavRoute.ALBUMS, tapped)
    }
}
