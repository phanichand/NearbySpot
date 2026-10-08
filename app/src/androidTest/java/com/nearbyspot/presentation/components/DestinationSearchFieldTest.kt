package com.nearbyspot.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpRect
import com.nearbyspot.presentation.theme.NearbySpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DestinationSearchFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun suggestionsUsePopupWithoutMovingTheFieldOrFollowingContent() {
        var expanded by mutableStateOf(false)
        lateinit var fieldBefore: DpRect
        lateinit var belowBefore: DpRect

        composeRule.setContent {
            NearbySpotTheme {
                Column {
                    DestinationSearchField(
                        query = "Goa",
                        suggestions = SUGGESTIONS,
                        expanded = expanded,
                        isLoading = false,
                        errorMessage = null,
                        onQueryChanged = {},
                        onFocusGained = {},
                        onClear = {},
                        onDismiss = { expanded = false },
                        onRetry = {},
                        onSuggestionSelected = {},
                        modifier = Modifier.width(300.dp),
                    )
                    Text("Following content")
                }
            }
        }

        fieldBefore = composeRule.onNodeWithTag(DESTINATION_SEARCH_FIELD_TAG)
            .getUnclippedBoundsInRoot()
        belowBefore = composeRule.onNodeWithText("Following content")
            .getUnclippedBoundsInRoot()

        composeRule.runOnIdle { expanded = true }

        val fieldAfter = composeRule.onNodeWithTag(DESTINATION_SEARCH_FIELD_TAG)
            .getUnclippedBoundsInRoot()
        val belowAfter = composeRule.onNodeWithText("Following content")
            .getUnclippedBoundsInRoot()
        val popup = composeRule.onNodeWithTag(DESTINATION_SUGGESTIONS_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertEquals(fieldBefore, fieldAfter)
        assertEquals(belowBefore, belowAfter)
        assertEquals(fieldAfter.right - fieldAfter.left, popup.right - popup.left)
        assertTrue(popup.top >= fieldAfter.bottom)
    }

    @Test
    fun suggestionSelectionUpdatesControlledQueryAndClosesPopup() {
        var query by mutableStateOf("Goa")
        var expanded by mutableStateOf(true)
        var selected: DestinationSuggestion? = null

        composeRule.setContent {
            NearbySpotTheme {
                DestinationSearchField(
                    query = query,
                    suggestions = SUGGESTIONS,
                    expanded = expanded,
                    isLoading = false,
                    errorMessage = null,
                    onQueryChanged = { query = it },
                    onFocusGained = {},
                    onClear = {
                        query = ""
                        expanded = false
                    },
                    onDismiss = { expanded = false },
                    onRetry = {},
                    onSuggestionSelected = {
                        selected = it
                        query = it.fullText
                        expanded = false
                    },
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        composeRule.onNodeWithText("Goa").performClick()

        composeRule.runOnIdle { assertEquals(SUGGESTIONS.first(), selected) }
        composeRule.onNodeWithTag(DESTINATION_SUGGESTIONS_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Goa, India").assertIsDisplayed()
    }

    @Test
    fun loadingAndRecoverableErrorAreRenderedInThePopup() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var retries = 0

        composeRule.setContent {
            NearbySpotTheme {
                DestinationSearchField(
                    query = "Goa",
                    suggestions = emptyList(),
                    expanded = true,
                    isLoading = loading,
                    errorMessage = error,
                    onQueryChanged = {},
                    onFocusGained = {},
                    onClear = {},
                    onDismiss = {},
                    onRetry = { retries++ },
                    onSuggestionSelected = {},
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        composeRule.onNodeWithTag(DESTINATION_LOADING_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            loading = false
            error = "Couldn’t load destinations"
        }
        composeRule.onNodeWithText("Couldn’t load destinations").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private companion object {
        val SUGGESTIONS = listOf(
            DestinationSuggestion(
                placeId = "goa",
                primaryText = "Goa",
                secondaryText = "India",
                fullText = "Goa, India",
            ),
        )
    }
}
