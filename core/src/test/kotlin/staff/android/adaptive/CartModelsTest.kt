package staff.android.adaptive

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CartModelsTest {
    private fun state(raw: String, revision: Long = 0) = CartState(
        lines = mapOf("camera" to CartLine(2, revision)),
        drafts = mapOf("camera" to QuantityDraft(raw, 0)), selectedId = "camera",
    )
    @Test fun draftIsSeparateFromAccepted() { val s = state("3"); assertEquals(2, s.lines["camera"]!!.quantity); assertEquals(3, s.candidate("camera")); assertTrue(s.canConfirm("camera")) }
    @Test fun emptyAndOutOfRangeCannotConfirm() { for (s in listOf("", "6", "-1", "3x")) assertFalse(state(s).canConfirm("camera")); assertEquals(0,state("0").candidate("camera")) }
    @Test fun staleDraftIsPreservedButCannotConfirm() { val s=state("3",1); assertTrue(s.isStale("camera")); assertFalse(s.canConfirm("camera")); assertEquals("3",s.drafts["camera"]!!.raw) }
    @Test fun busyAndLoadingCannotConfirm() { assertFalse(state("3").copy(busy=true).canConfirm("camera")); assertFalse(state("3").copy(lines=emptyMap()).canConfirm("camera")) }
    @Test fun localBudgetHasExplicitBoundary() { assertFalse(showsTwoPanes(839.99f)); assertTrue(showsTwoPanes(840f)); assertTrue(showsTwoPanes(1000f)) }
    @Test fun episodeProgressRejectsInvalidContracts() { assertThrows(IllegalArgumentException::class.java) { EpisodeUi("id","title",false,101) }; assertThrows(IllegalArgumentException::class.java) { EpisodeUi("","title",false,0) } }
}
