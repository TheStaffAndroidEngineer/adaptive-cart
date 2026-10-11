package staff.android.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CartLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun model() = CartState(
        lines = mapOf("camera" to CartLine(), "tea" to CartLine()),
        drafts = mapOf("camera" to QuantityDraft("3",0)), selectedId = "camera",
    )
    private val state = mutableStateOf(model())
    private val size = mutableStateOf(DpSize(320.dp,600.dp))
    private val accepted = mutableListOf<String>()
    @Composable private fun Screen() {
        val inputMode = LocalInputModeManager.current
        SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
        CartScreen(
            state.value, {state.value=state.value.copy(selectedId=it)}, {},
            {id,raw -> state.value=state.value.copy(drafts=state.value.drafts + (id to QuantityDraft(raw,0)))},
            {id -> state.value=state.value.copy(drafts=state.value.drafts - id)}, {},
            {accepted+=it}, {}, {}, Modifier.fillMaxSize(),
        )
    }
    @Test fun brokenRowCannotGrantItsPreferredButtonWidth() {
        compose.setContent { MaterialTheme { QuantityLayoutProbe(true) } }
        val button=compose.onNode(hasText("Confirm") and hasClickAction()).getUnclippedBoundsInRoot()
        println("C8_BROKEN_BUTTON_BOUNDS_DP=$button")
        assertTrue(button.width.value < 104f)
        compose.onNode(hasSetTextAction()).performTextReplacement("3")
        compose.onNodeWithText("Accepted: 2").assertExists()
    }
    @Test fun repairedLargeTextCanScrollToAnExplicitConfirmation() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                MaterialTheme { QuantityLayoutProbe(false) }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("3")
        compose.onNodeWithText("Accepted: 2").assertExists()
        compose.onNode(hasText("Confirm") and hasClickAction()).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Accepted: 3").assertExists()
    }
    @Test fun resizeKeepsTheSameFocusedDraftWithoutAccepting() {
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.value)) { CartTheme(dynamic=false) { Screen() } } }
        compose.onNodeWithTag("one-pane").assertExists()
        compose.onNodeWithTag("draft").assertIsFocused().performTextReplacement("4")
        compose.runOnIdle { size.value=DpSize(1000.dp,600.dp) }
        compose.onNodeWithTag("two-panes").assertExists()
        compose.onNodeWithTag("item-tea").assertExists()
        compose.onNodeWithTag("draft").assertIsFocused().assertTextContains("4")
        compose.runOnIdle { size.value=DpSize(320.dp,600.dp); assertTrue(accepted.isEmpty()) }
        compose.onNodeWithTag("draft").assertIsFocused().assertTextContains("4")
    }
    @Test fun cartReturnKeepsDraftAndReturnsKeyboardFocusToItsRow() {
        compose.setContent { CartTheme(dynamic=false) { Screen() } }
        compose.onNodeWithTag("back-cart").performClick()
        compose.onNodeWithTag("item-camera").assertIsFocused().performClick()
        compose.onNodeWithTag("draft").assertTextContains("3")
        compose.runOnIdle { assertTrue(accepted.isEmpty()) }
    }
    @Test fun imeDoneUsesTheSameGuardedExplicitAction() {
        compose.setContent { CartTheme(dynamic=false) { Screen() } }
        compose.onNodeWithTag("draft").performTextReplacement("")
        compose.onNodeWithTag("draft").performImeAction()
        compose.runOnIdle { assertTrue(accepted.isEmpty()) }
        compose.onNodeWithTag("draft").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error,"Enter a whole quantity from 0 to 5."))
        compose.onNodeWithTag("draft").performTextReplacement("3")
        compose.onNodeWithTag("draft").performImeAction()
        compose.runOnIdle { assertEquals(listOf("camera"),accepted) }
    }
    @Test fun germanLargeTextWrapsSeparateActionTargets() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp,600.dp))
                then DeviceConfigurationOverride.FontScale(2f)
                then DeviceConfigurationOverride.Locales(LocaleList("de"))) {
                CartTheme(dynamic=false) { Screen() }
            }
        }
        compose.onNodeWithTag("cancel").performScrollTo()
        val confirm=compose.onNodeWithTag("confirm").getUnclippedBoundsInRoot()
        val cancel=compose.onNodeWithTag("cancel").getUnclippedBoundsInRoot()
        assertTrue(cancel.top >= confirm.bottom)
        compose.onNodeWithText("Menge bestätigen").assertExists()
        compose.onNodeWithTag("confirm").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
    @Test fun arabicUsesRtlAndLocalizedQuantityWithoutReversingReadingMeaning() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1000.dp,600.dp))
                then DeviceConfigurationOverride.Locales(LocaleList("ar-u-nu-arab"))) {
                CartTheme(dynamic=false) { Screen() }
            }
        }
        val item=compose.onNodeWithTag("item-camera").getUnclippedBoundsInRoot()
        val editor=compose.onNodeWithTag("editor-scroll").getUnclippedBoundsInRoot()
        assertTrue(item.left >= editor.right)
        compose.onNodeWithTag("accepted").assertTextContains("٢", substring = true)
        compose.onNodeWithTag("confirm").assertHasClickAction()
    }
    @Test fun failureForCameraDoesNotAppearAsTeaFailure() {
        state.value=model().copy(selectedId="tea",issue=CartIssue.Save,issueItemId="camera")
        compose.setContent { CartTheme(dynamic=false) { Screen() } }
        compose.onNodeWithText("Could not save. Your draft is still here.").assertDoesNotExist()
        compose.runOnIdle { state.value=state.value.copy(selectedId="camera") }
        compose.onNodeWithText("Could not save. Your draft is still here.").assertExists()
    }
    @Test fun pendingEditIsVisiblyDisabledAndLoadingHidesTheForm() {
        state.value=model().copy(selectedId="tea",busy=true)
        compose.setContent { CartTheme(dynamic=false) { Screen() } }
        compose.onNodeWithTag("edit").assertIsNotEnabled()
        compose.runOnIdle { state.value=CartState(issue=CartIssue.Load) }
        compose.onNodeWithTag("edit").assertDoesNotExist()
        compose.onNodeWithTag("reset").assertDoesNotExist()
        compose.onNodeWithText("Retry loading").assertHasClickAction()
    }
    @Test fun helpAnimationDoesNotOwnTheAcceptanceAction() {
        compose.setContent { CartTheme(dynamic=false) { Screen() } }
        compose.onNodeWithTag("help").performScrollTo().performClick()
        compose.onNodeWithTag("help-text").assertExists()
        compose.onNodeWithTag("confirm").performScrollTo().assertHasClickAction()
        compose.runOnIdle { assertTrue(accepted.isEmpty()) }
        compose.onNodeWithTag("help").performClick()
        compose.onNodeWithTag("help-text").assertDoesNotExist()
    }
}
