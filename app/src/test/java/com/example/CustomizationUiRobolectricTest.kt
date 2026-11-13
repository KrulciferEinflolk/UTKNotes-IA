package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.local.ai.AiApiSettingsState
import com.example.data.local.ai.AiStudioModelInfo
import com.example.data.local.ai.ApiKeyValidationState
import com.example.ui.GoogleAiStudioEngineCard
import com.example.ui.HexColorBox
import com.example.ui.HexColorPickerDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TypographyDropdownMenu
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomizationUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `GoogleAiStudioEngineCard hides model section before verification and removes legacy inputs`() {
        val unverifiedState = AiApiSettingsState(
            hasApiKey = false,
            isConnectionVerified = false,
            validationState = ApiKeyValidationState.Idle
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    GoogleAiStudioEngineCard(
                        isCloudActive = true,
                        aiSettings = unverifiedState,
                        onSelectEngine = {},
                        onSaveApiKey = {},
                        onClearApiKey = {},
                        onTestConnection = {},
                        onSelectModel = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_studio_api_key_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_connection_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Probar conexión").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar clave").assertDoesNotExist()
        composeTestRule.onNodeWithTag("gemini_model_selector_section").assertDoesNotExist()
        composeTestRule.onNodeWithText("Modelo de Gemini a usar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Modelo personalizado").assertDoesNotExist()
    }

    @Test
    fun `GoogleAiStudioEngineCard reveals model dropdown after verification and selects model`() {
        var selectedModelResult: String? = null
        val verifiedState = AiApiSettingsState(
            hasApiKey = true,
            maskedKeyPreview = "AIza••••1234",
            isConnectionVerified = true,
            selectedModel = "gemini-2.5-flash",
            discoveredModels = listOf(
                AiStudioModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
                AiStudioModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro")
            ),
            validationState = ApiKeyValidationState.Valid(2)
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    GoogleAiStudioEngineCard(
                        isCloudActive = true,
                        aiSettings = verifiedState,
                        onSelectEngine = {},
                        onSaveApiKey = {},
                        onClearApiKey = {},
                        onTestConnection = {},
                        onSelectModel = { selectedModelResult = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("gemini_model_selector_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_model_dropdown_anchor").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Modelo personalizado").assertDoesNotExist()

        composeTestRule.onNodeWithTag("gemini_model_dropdown_anchor").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("gemini_model_option_gemini-2.5-pro").performClick()
        composeTestRule.waitForIdle()

        assertEquals("gemini-2.5-pro", selectedModelResult)
    }

    @Test
    fun `HexColorBox renders direct rainbow slider with plus button opening HSV wheel mini dialog`() {
        var chosenColor: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                HexColorBox(
                    selectedColorHex = "#907CFF",
                    onColorSelected = { chosenColor = it },
                    label = "Color de Cubierta (HEX)"
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rainbow_hue_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_color_wheel_plus_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_wheel_mini_dialog").assertDoesNotExist()

        composeTestRule.onNodeWithTag("open_color_wheel_plus_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_wheel_mini_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hsv_color_wheel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hsv_value_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_tab_RGB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_tab_HSV").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_tab_HEX").assertIsDisplayed()

        composeTestRule.onNodeWithTag("color_tab_HEX").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wheel_hex_input").performTextReplacement("#4B9CD3")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_wheel_apply_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("#4B9CD3", chosenColor)
    }

    @Test
    fun `HexColorPickerDialog supports RGB HSV and HEX tabs`() {
        var chosenColor: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                HexColorPickerDialog(
                    initialColorHex = "#FF0000",
                    onColorSelected = { chosenColor = it },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hsv_input_h").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_tab_RGB").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rgb_input_r").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_tab_HEX").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wheel_hex_input").performTextReplacement("#00A86B")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_wheel_apply_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("#00A86B", chosenColor)
    }

    @Test
    fun `TypographyDropdownMenu displays recent and all fonts sections inline without modal sheet`() {
        var chosenFont: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) {
                        Text("Sans")
                    }
                    TypographyDropdownMenu(
                        expanded = expanded,
                        selectedFontId = "Sans",
                        onFontSelected = { chosenFont = it },
                        onDismiss = { expanded = false }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sans").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("typography_dropdown_menu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fuentes recientes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Todas las fuentes").assertIsDisplayed()

        composeTestRule.onNodeWithTag("font_option_Montserrat").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Montserrat", chosenFont)
    }
}
