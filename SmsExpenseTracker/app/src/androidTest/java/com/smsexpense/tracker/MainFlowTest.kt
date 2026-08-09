package com.smsexpense.tracker

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI flow (runs on an emulator/device):
 * skip onboarding → create a category → simulate a payment through the real
 * pipeline via the Debug screen → verify it appears on the dashboard →
 * open it → categorize it → dashboard updates.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun skipOnboardingIfShown() {
        val skip = composeRule.onAllNodes(hasText("Skip for now"))
        if (skip.fetchSemanticsNodes().isNotEmpty()) {
            skip.onFirst().performClick()
        }
    }

    @Test
    fun fullFlow_createCategory_simulatePayment_categorize() {
        skipOnboardingIfShown()

        // --- Create a category ---
        composeRule.onNodeWithContentDescription("Categories").performClick()
        composeRule.onNodeWithContentDescription("Add category").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Coffee")
        composeRule.onNodeWithText("Icon (emoji)").performTextInput("☕")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Coffee").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()

        // --- Simulate a payment through the real pipeline ---
        composeRule.onNodeWithContentDescription("Debug").performClick()
        // Bypass sender filter because no Sender ID is configured in a fresh install.
        composeRule.onNodeWithText("Respect Sender ID filter").performClick()
        composeRule.onNodeWithText("Simulate SMS").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Saved payment", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back").performClick()

        // --- Payment appears on the dashboard as uncategorized ---
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Needs categorizing", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Coffee Shop").performClick()

        // --- Categorize it from the detail screen ---
        composeRule.onNodeWithText("☕ Coffee").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        // --- Dashboard no longer shows the uncategorized section ---
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Needs categorizing", substring = true))
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
