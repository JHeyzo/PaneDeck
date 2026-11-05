package io.github.jheyzo.panedeck.workspace

/**
 * Ported from Panes Workbench 3.0.5 (yishisanren, Apache License 2.0),
 * via the zealler9560 mirror, with attribution retained. Only the package
 * name and the size bound changed (see below).
 *
 * PaneDeck change vs Panes: packages is 1..4, not 1..5, because the
 * workspace is 1 main window + 3 slots. Panes also falls back to an
 * already-occupied slot when no empty slot exists; PaneDeck does not,
 * because its slot launch path cannot replace an occupied slot (it
 * fails with "Slot is occupied"). Here a round that does not fit in
 * the currently empty slots throws NoSpace, and the caller reports it
 * instead of silently replacing an app the user is looking at.
 */
/** Reserve existing matches first, then empty or replaceable windows. */
internal object WorkspaceSearchPlacement {
    class NoSpace : IllegalStateException("Not enough empty slots")
    fun plan(packages: List<String>, slots: List<String?>, promotedSlot: Int? = null,
        unavailable: Set<Int> = emptySet()): List<Int> {
        require(packages.size in 1..4 && packages.distinct().size == packages.size)
        val available = slots.toMutableList()
        promotedSlot?.let { index ->
            require(index in available.indices && available[index] == packages.first())
            available[index] = null
        }
        val targets = packages.drop(1)
        val matches = targets.associateWith { name ->
            available.indices.firstOrNull { it !in unavailable && available[it] == name }
        }
        val reserved = matches.values.filterNotNull().toMutableSet()
        return targets.map { name ->
            matches[name]?.let { return@map it }
            val candidates = available.indices.filter { it !in reserved && it !in unavailable }
            val index = candidates.firstOrNull { available[it] == null }
                ?: throw NoSpace()
            check(reserved.add(index))
            index
        }
    }
}
