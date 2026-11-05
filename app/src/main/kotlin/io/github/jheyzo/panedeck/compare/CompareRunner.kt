package io.github.jheyzo.panedeck.compare

import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.jheyzo.panedeck.logging.NeXtepLog

/**
 * Runs one compare round on the main thread, one window at a time.
 *
 * Upstream workspace is 1 main + 3 slots, so a round is at most 4 apps:
 * the first installed target goes to the main window (external deep link,
 * no launcher category), the rest go to slots 0, 1, 2 in order.
 * A launch failure for one app is logged and skipped, not fatal to the
 * round; a round with no installed target at all does nothing.
 *
 * Launches are spaced out because a slot launch fails while a previous
 * transition is still pending. The delay is a heuristic, not a guarantee:
 * on a slow device a slot may still report pending, in which case the
 * user retries after the workspace settles. Whether a slot deep link
 * survives upstream's launcher-category normalisation is unverified
 * until on-device testing (see DESIGN_COMPARE.md).
 */
internal class CompareRunner(
    private val openMain: (Intent) -> Boolean,
    private val launchSlot: (Intent, Int) -> Boolean,
    private val canOpen: (Intent) -> Boolean,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    fun run(mode: CompareIntents.Mode, query: String): Boolean {
        val requested = runCatching { CompareIntents.searches(mode, query) }.getOrNull()
            ?: return false
        // Meituan fallback, as in Panes 3.0.5: the standalone takeout app
        // if the main Meituan search entry does not resolve.
        val available = requested.mapNotNull { target ->
            if (canOpen(target.intent)) target
            else if (target.intent.`package` == "com.sankuai.meituan") {
                val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                    "meituanwaimai://waimai.meituan.com/search?query=${android.net.Uri.encode(query.trim())}",
                )).setPackage("com.sankuai.meituan.takeoutnew").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (canOpen(fallback)) target.copy(intent = fallback) else null
            } else null
        }.take(4)
        if (available.isEmpty()) {
            NeXtepLog.warn("compare", "No installed app supports this compare mode")
            return false
        }
        val steps = ArrayDeque<() -> Boolean>()
        steps += { openMain(available[0].intent) }
        available.drop(1).forEachIndexed { index, target ->
            steps += { launchSlot(target.intent, index) }
        }
        var stepIndex = 0
        val step = object : Runnable {
            override fun run() {
                if (steps.isEmpty()) return
                val target = available[stepIndex]
                val ok = runCatching { steps.removeFirst().invoke() }.getOrDefault(false)
                if (!ok) NeXtepLog.warn("compare", "Launch failed app=${target.name} step=$stepIndex")
                stepIndex++
                if (steps.isNotEmpty()) handler.postDelayed(this, STEP_DELAY_MS)
            }
        }
        handler.post(step)
        return true
    }

    private companion object {
        const val STEP_DELAY_MS = 900L
    }
}
