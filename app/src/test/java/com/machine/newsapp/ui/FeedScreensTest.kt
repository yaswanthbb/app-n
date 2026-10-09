package com.machine.newsapp.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.machine.newsapp.data.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class MemoryTokenStore : PublishTokenStore {
    var token: String? = "test-token"
    var reads = 0
    override suspend fun hasToken() = token != null
    override suspend fun readForDelete(): String? { reads++; return token }
    override suspend fun save(token: String) { this.token = token }
    override suspend fun clear() { token = null }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `old deal opens with description and claim without optional sections`() {
        val deal = Deal("Legacy free tool", "The entire original description.", "https://example.com/tool", "Example source", "Dev tools")
        compose.setContent { NewsTheme { DealDetailScreen(deal, {}, {}) } }
        compose.onNodeWithText(deal.title).assertIsDisplayed()
        compose.onNodeWithText(deal.description).assertIsDisplayed()
        compose.onNodeWithText(deal.source).assertIsDisplayed()
        compose.onNodeWithText("Claim this offer ↗").assertIsDisplayed()
        compose.onNodeWithText("What you get").assertDoesNotExist()
        compose.onNodeWithText("How to claim").assertDoesNotExist()
    }
    @Test fun `new deal renders optional details and numbered steps`() {
        val deal = Deal("Detailed free tool", "Full description.", "https://example.com/tool", "Example", "AI", "2026-12-31", offerDetails = "Free local model access.", claimSteps = listOf("Download the model", "Run it locally"))
        compose.setContent { NewsTheme { DealDetailScreen(deal, {}, {}) } }
        compose.onNodeWithText("What you get").assertExists()
        compose.onNodeWithText("Free local model access.").assertExists()
        compose.onNodeWithText("How to claim").assertExists()
        compose.onNodeWithText("1.").assertExists()
        compose.onNodeWithText("2.").assertExists()
        compose.onNodeWithText("Run it locally").assertExists()
        compose.onNodeWithText("Expires Thursday, December 31, 2026").assertExists()
    }
    @Test fun `legacy pick renders full body date and link`() {
        val pick = Pick("A thoughtful resource", "The complete note, with all of its context.", "https://example.com/pick", "2026-10-09")
        compose.setContent { NewsTheme { PickDetailScreen(pick, {}, {}) } }
        compose.onNodeWithText(pick.title).assertIsDisplayed()
        compose.onNodeWithText(pick.body).assertIsDisplayed()
        compose.onNodeWithText("Friday, October 9, 2026").assertIsDisplayed()
        compose.onNodeWithText("Open this pick ↗").assertIsDisplayed()
    }
    @Test fun `card navigates to detail and delete only sends after confirmation`() {
        val api = FakeApi().apply { dealItems = dealItems.map { it.copy(id = 37) } }
        val deletes = FakeDeleteApi()
        val store = MemoryTokenStore()
        val repository = FeedRepository(MemoryDao(), api, Json, "key", "https://example.com/api/deals", "https://example.com/api/picks", deletes)
        val vm = FeedViewModel(repository, store)
        compose.setContent { NewsTheme { NewsRoot(vm, null, {}, true, {}) } }
        compose.onNodeWithText("DEALS").performClick()
        compose.onNodeWithText("Free model").performClick()
        compose.onNodeWithText("Deal details").assertIsDisplayed()
        compose.onNodeWithText("Claim this offer ↗").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Item options").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this deal?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, store.reads)
        assertTrue(deletes.requests.isEmpty())
        compose.onNodeWithContentDescription("Item options").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { deletes.requests.size == 1 }
        compose.onNodeWithText("No free offers right now").assertExists()
        assertEquals(1, store.reads)
        assertEquals("test-token", deletes.requests.single().second)
    }
}
