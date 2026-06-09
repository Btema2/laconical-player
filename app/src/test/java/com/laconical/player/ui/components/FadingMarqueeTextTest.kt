package com.laconical.player.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class FadingMarqueeTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders text content`() {
        composeTestRule.setContent {
            FadingMarqueeText(
                text = "Test Song Title",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                isScrolling = false,
            )
        }
        composeTestRule.onNodeWithText("Test Song Title").assertIsDisplayed()
    }

    @Test
    fun `renders with scrolling enabled without crashing`() {
        composeTestRule.setContent {
            FadingMarqueeText(
                text = "A Very Long Song Title That Should Overflow The Container Width",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                isScrolling = true,
            )
        }
        composeTestRule.onNodeWithText(
            "A Very Long Song Title That Should Overflow The Container Width",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `fills container width when text is shorter than container`() {
        val containerWidthDp = 200.dp
        var nodeWidthPx = 0f
        var containerWidthPx = 0f

        composeTestRule.setContent {
            val localDensity = LocalDensity.current
            containerWidthPx = with(localDensity) { containerWidthDp.toPx() }

            Box(
                modifier = Modifier.width(containerWidthDp)
            ) {
                FadingMarqueeText(
                    text = "Hi",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    isScrolling = false,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        nodeWidthPx = coords.size.width.toFloat()
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(containerWidthPx, nodeWidthPx, 1f)
    }
}
