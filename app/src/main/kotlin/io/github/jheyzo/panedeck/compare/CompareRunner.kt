package io.github.jheyzo.panedeck.compare

import android.content.Intent
import io.github.jheyzo.panedeck.logging.NeXtepLog
import io.github.jheyzo.panedeck.workspace.WorkspaceLaunchSequence

/**
 * Runs one compare round through SlotTaskCoordinator.openSearches
 * (ported from Panes 3.0.5): deep links are resolved with
 * WorkspaceIntentResolver (no CATEGORY_LAUNCHER, data preserved),
 * the first target goes to the main window and the rest to slots
 * chosen by WorkspaceSearchPlacement (existing matches first, then
 * empty slots), and each launch is observed ready at its destination
 * before the next one starts. No fixed 900 ms spacing any more.
 *
 * Per-target fallback, in order: custom scheme; for Meituan, the
 * standalone takeout app's custom scheme (as in Panes 3.0.5); for
 * shopping targets, the https fallback in CompareIntents (package-
 * pinned to the same app, so it stays in-app via App Links when the
 * app handles that host). A target with no resolvable entry is
 * skipped; a round with no installed target at all does nothing.
 * A launch failure stops the sequence (Panes behaviour) and is
 * reported with the failed index and how many launches completed;
 * launches that already completed are kept, not rolled back.
 */
internal class CompareRunner(
    private val openSearches: (List<Intent>, (Result<Unit>) -> Unit) -> Boolean,
    private val canOpen: (Intent) -> Boolean,
) {
    /**
     * [selectedNames] limits the round to named targets (null = all
     * targets, e.g. the dialog since v17 and the Settings broadcast).
     * The workspace fits 4 windows (1 main + 3 slots), so the first 4
     * available targets run.
     */
    fun run(mode: CompareIntents.Mode, query: String, selectedNames: Set<String>? = null): Boolean {
        val requested = runCatching { CompareIntents.searches(mode, query) }.getOrNull()
            ?: return false
        val filtered = if (selectedNames == null) requested
        else requested.filter { it.name in selectedNames }
        val available = filtered.mapNotNull { target ->
            when {
                canOpen(target.intent) -> target.intent
                target.intent.`package` == "com.sankuai.meituan" -> {
                    val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                        "meituanwaimai://waimai.meituan.com/search?query=${android.net.Uri.encode(query.trim())}",
                    )).setPackage("com.sankuai.meituan.takeoutnew").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (canOpen(fallback)) {
                        NeXtepLog.info("compare", "Meituan main entry unresolvable; using takeout app")
                        fallback
                    } else null
                }
                target.httpsFallback != null && canOpen(target.httpsFallback) -> {
                    NeXtepLog.info("compare", "Custom scheme unresolvable for ${target.name}; using https fallback")
                    target.httpsFallback
                }
                else -> null
            }
        }.take(4)
        if (available.isEmpty()) {
            NeXtepLog.warn("compare", "No installed app supports this compare mode")
            return false
        }
        val started = openSearches(available) { result ->
            result.onSuccess {
                NeXtepLog.info("compare", "Compare round completed targets=${available.size}")
            }.onFailure { error ->
                val failure = error as? WorkspaceLaunchSequence.Failure
                if (failure != null) {
                    NeXtepLog.warn(
                        "compare",
                        "Compare round stopped index=${failure.index} completed=${failure.completed} " +
                            "stage=${failure.stage}",
                        failure.cause,
                    )
                } else {
                    NeXtepLog.warn("compare", "Compare round failed", error)
                }
            }
        }
        if (!started) {
            NeXtepLog.warn("compare", "Compare sequence could not start")
            return false
        }
        return true
    }
}
