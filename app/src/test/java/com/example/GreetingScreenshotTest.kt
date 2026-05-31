package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.BenViewModel
import com.example.ui.BenScreen
import androidx.room.Room
import com.example.data.BenDatabase

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setupTestDb() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val testDb = Room.inMemoryDatabaseBuilder(context, BenDatabase::class.java)
      .allowMainThreadQueries()
      .setQueryExecutor { it.run() }
      .setTransactionExecutor { it.run() }
      .build()
    BenDatabase.setTestDatabase(testDb)
  }

  @Test
  fun test_ben_screen_render() {
    setupTestDb()
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = BenViewModel(application)
    composeTestRule.setContent {
      MyApplicationTheme {
        BenScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun test_comprehensive_interactions() {
    setupTestDb()
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = BenViewModel(application)
    composeTestRule.setContent {
      MyApplicationTheme {
        BenScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()

    // 1. Click Pet Area
    composeTestRule.onNodeWithTag("ben_pet_area").performClick()
    composeTestRule.waitForIdle()

    // 2. Click Navigation button: Feed
    composeTestRule.onNodeWithTag("nav_feed").performClick()
    composeTestRule.waitForIdle()

    // Feed carrot
    composeTestRule.onNodeWithTag("feed_button_carrot").performClick()
    composeTestRule.waitForIdle()

    // 3. Click Navigation button: Wardrobe
    composeTestRule.onNodeWithTag("nav_wardrobe").performClick()
    composeTestRule.waitForIdle()

    // Equip default
    composeTestRule.onNodeWithTag("equip_button_classic").performClick()
    composeTestRule.waitForIdle()

    // 4. Click Navigation button: Room (Environment)
    composeTestRule.onNodeWithTag("nav_room").performClick()
    composeTestRule.waitForIdle()

    // 5. Test sleeping and waking up
    composeTestRule.onNodeWithTag("nav_sleep").performClick()
    composeTestRule.waitUntil(3000) {
        viewModel.animationState.value == com.example.viewmodel.BenAnimation.SLEEPING
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_wake").performClick()
    composeTestRule.waitUntil(3000) {
        viewModel.animationState.value == com.example.viewmodel.BenAnimation.IDLE
    }
    composeTestRule.waitForIdle()

    // 6. Test Whack-A-Carrot mini-game start
    composeTestRule.onNodeWithTag("nav_play_game").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Ben Rabbit Virtual Pet")
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
