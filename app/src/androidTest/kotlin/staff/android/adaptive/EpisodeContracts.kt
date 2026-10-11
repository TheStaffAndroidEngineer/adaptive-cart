package staff.android.adaptive

import androidx.compose.foundation.layout.*
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

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExerciseOnly

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExplicitSelection

abstract class EpisodeContracts {
    @get:Rule val compose=createComposeRule()
    @Composable abstract fun Render(ui:EpisodeUi,play:(String)->Unit,download:(String)->Unit,remove:(String)->Unit)
    private val ui=mutableStateOf(EpisodeUi("a","Field recording",false,0))
    private val callback=mutableStateOf<(String)->Unit>({})
    private val downloads=mutableListOf<String>()
    private val removals=mutableListOf<String>()
    private fun show(width:Int=1000,scale:Float=1f,locale:String="en") {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp,700.dp))
                then DeviceConfigurationOverride.FontScale(scale)
                then DeviceConfigurationOverride.Locales(LocaleList(locale))) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                CartTheme(dynamic=false) {Box(Modifier.fillMaxSize().padding(16.dp)) {Render(ui.value,callback.value,{downloads+=it},{removals+=it})}}
            }
        }
    }
    @Test fun playIsAnIndependentKeyboardAction() {
        val calls=mutableListOf<String>();callback.value={calls+=it};show()
        compose.onNodeWithTag("play-pause").assertHasClickAction()
            .performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        compose.onNodeWithTag("play-pause").performKeyInput {pressKey(Key.Enter)}
        compose.runOnIdle {assertEquals(listOf("a"),calls)}
    }
    @Test fun progressUpdatesPreservePlayFocusAndNeverInvokeActions() {
        var calls=0;callback.value={calls++};show()
        compose.onNodeWithTag("play-pause").performSemanticsAction(SemanticsActions.RequestFocus) {it()}
        compose.runOnIdle {ui.value=ui.value.copy(downloadPercent=37)}
        compose.onNodeWithTag("play-pause").assertIsFocused()
        compose.runOnIdle {assertEquals(0,calls);assertTrue(downloads.isEmpty());assertTrue(removals.isEmpty())}
    }
    @Test fun progressIsAnExposedNamedRange() {
        ui.value=ui.value.copy(downloadPercent=37);show()
        compose.onNodeWithTag("episode-progress")
            .assertContentDescriptionEquals("Download progress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.37f,0f..1f)))
    }
    @Test fun parentProgressGatesCommandsAndKeepsSubmissionExplicit() {
        show();compose.onNodeWithTag("remove").assertIsNotEnabled()
        compose.onNodeWithTag("download").performClick()
        compose.runOnIdle {assertEquals(listOf("a"),downloads);ui.value=ui.value.copy(downloadPercent=37)}
        compose.onNodeWithTag("download").assertIsNotEnabled();compose.onNodeWithTag("remove").assertIsNotEnabled()
        compose.runOnIdle {ui.value=ui.value.copy(downloadPercent=100)}
        compose.onNodeWithTag("remove").assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(listOf("a"),removals)}
    }
    @Test fun currentCallbackIsUsedWithoutProgressChanging() {
        val calls=mutableListOf<String>();callback.value={calls+="old:$it"};show()
        compose.onNodeWithTag("play-pause").performTouchInput {click()}
        compose.runOnIdle {
            assertEquals(listOf("old:a"),calls)
            calls.clear()
            callback.value={calls+="new:$it"}
        }
        compose.onNodeWithTag("play-pause").performTouchInput {click()}
        compose.runOnIdle {assertEquals(listOf("new:a"),calls)}
    }
    @Test fun longGermanLabelsAtLargeTextRemainReachableInNarrowSpace() {
        show(320,2f,"de")
        for(tag in listOf("play-pause","download","remove")) {
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction()
        }
        val play=compose.onNodeWithTag("play-pause").getUnclippedBoundsInRoot()
        val download=compose.onNodeWithTag("download").getUnclippedBoundsInRoot()
        assertTrue(download.top>=play.bottom)
    }
}
class EpisodeReferenceTest:EpisodeContracts() {
    @Composable override fun Render(ui:EpisodeUi,play:(String)->Unit,download:(String)->Unit,remove:(String)->Unit) {
        staff.android.adaptive.solution.EpisodeActionBar(ui,play,download,remove)
    }
}
@ExerciseOnly
@ExplicitSelection
class EpisodeLearnerTest:EpisodeContracts() {
    @Composable override fun Render(ui:EpisodeUi,play:(String)->Unit,download:(String)->Unit,remove:(String)->Unit) {
        staff.android.adaptive.exercise.EpisodeActionBar(ui,play,download,remove)
    }
}
