package com.animationstudio.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MainWorkspaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createProjectSelectToolsAndInspectTimeline() {
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").performTextReplacement("Instrumented scene")
        compose.onNodeWithText("Create editable project").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Animation drawing canvas. Use touch or stylus to edit with the selected tool.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Rectangle").performClick()
        compose.onNodeWithText("Rectangle").assertExists()
        compose.onNodeWithText("Blank cel").assertExists()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithText("Instrumented scene").assertExists()
    }

    @Test fun exportConfigurationIsReachable() {
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Create editable project").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Render").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Render").performClick()
        compose.onNodeWithText("Configure export").performClick()
        compose.onNodeWithText("Render animation").assertExists()
        compose.onNodeWithText("H.264 MP4 export is silent in this version. Codec availability varies by device.", substring = true).assertExists()
    }
}
