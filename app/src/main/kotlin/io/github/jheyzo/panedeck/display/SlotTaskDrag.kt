package io.github.jheyzo.panedeck.display

/** Process-local ownership token; never accept arbitrary task IDs from external drag data. */
data class SlotTaskDrag(val slotIndex: Int, val taskId: Int, val displayId: Int) {
    var dropPoint: android.graphics.PointF? = null

    companion object {
        const val MIME_TYPE = "application/vnd.io.github.jheyzo.panedeck.slot-task"
    }
}
