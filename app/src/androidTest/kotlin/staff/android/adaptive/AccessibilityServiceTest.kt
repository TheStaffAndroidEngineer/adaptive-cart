package staff.android.adaptive

import android.app.UiAutomation
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceOnly

/** Separate from semantics tests: requires an already enabled TalkBack service.
 * Exercises accessibility-node actions; does not assert speech or gesture order. */
@ServiceOnly
@ExplicitSelection
class AccessibilityServiceTest {
    @Test fun enabledTalkBackCoexistsWithExplicitAccessibleAcceptance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        val manager = instrumentation.targetContext.getSystemService(
            Context.ACCESSIBILITY_SERVICE,
        ) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).map { it.id }
        assertTrue("Enable TalkBack before -PserviceCheck: $enabled",
            enabled.any { it.startsWith("com.google.android.marvin.talkback/") })
        Log.i("C8ServiceEvidence", "enabled=$enabled; input=accessibility-node-actions")
        ActivityScenario.launch(MainActivity::class.java).use {
            fun search(node: AccessibilityNodeInfo?, tag: String): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.viewIdResourceName == tag) return node
                for (index in 0 until node.childCount) {
                    search(node.getChild(index), tag)?.let { found -> return found }
                }
                return null
            }
            fun node(tag: String, predicate: (AccessibilityNodeInfo) -> Boolean = { true }): AccessibilityNodeInfo {
                val deadline = SystemClock.uptimeMillis() + 8_000
                var scrollAction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                var scrolls = 0
                fun scrollContainer(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.isScrollable && node.isVisibleToUser) return node
                    for (index in 0 until node.childCount) {
                        scrollContainer(node.getChild(index))?.let { return it }
                    }
                    return null
                }
                while (SystemClock.uptimeMillis() < deadline) {
                    // Never retain a container across a scroll or screen change.
                    val root = automation.rootInActiveWindow
                    val found = search(root, tag)
                    if (found != null && found.isVisibleToUser) {
                        if (predicate(found)) return found
                    } else if (scrolls < 12) {
                        val pane = search(root, "editor-scroll")
                            ?.takeIf { it.isScrollable && it.isVisibleToUser }
                            ?: scrollContainer(root)
                        if (pane != null) {
                            val moved = pane.performAction(scrollAction)
                            scrolls++
                            Log.i("C8ServiceEvidence", "target=$tag; scroll=$scrollAction; moved=$moved")
                            if (!moved) {
                                scrollAction = if (scrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                                else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            }
                        }
                    }
                    SystemClock.sleep(100)
                }
                throw AssertionError("Accessible node unavailable: $tag")
            }
            fun click(tag: String) {
                assertTrue("ACTION_CLICK $tag", node(tag).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            click("lab-controls")
            click("reset")
            automation.waitForIdle(200, 5_000)
            node("reset") { it.isEnabled }
            click("item-camera")
            click("edit")
            val field = node("draft")
            assertTrue(field.isEditable)
            fun replace(text: String) {
                assertTrue(node("draft").performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }))
            }
            replace("")
            val invalid = node("draft") { it.isContentInvalid && !it.error.isNullOrBlank() }
            assertFalse(node("confirm") { !it.isEnabled }.isEnabled)
            Log.i("C8ServiceEvidence", "invalid=${invalid.isContentInvalid}; error=${invalid.error}; confirmEnabled=false")
            replace("3")
            val confirm = node("confirm") { it.isEnabled }
            val focused = automation.executeAndWaitForEvent({
                assertTrue(confirm.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            }, { event -> event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                event.source?.viewIdResourceName == "confirm" }, 5_000)
            assertEquals("confirm", focused.source?.viewIdResourceName)
            click("confirm")
            val accepted = node("accepted") { it.text?.contains("3") == true }
            Log.i("C8ServiceEvidence", "accessibilityFocus=confirm; explicitAction=ACTION_CLICK; accepted=${accepted.text}")
            node("edit")
        }
    }
}
