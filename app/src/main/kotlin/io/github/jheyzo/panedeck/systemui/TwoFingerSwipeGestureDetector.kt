package io.github.jheyzo.panedeck.systemui

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Recognises a deliberate two-finger vertical swipe (up or down) that toggles
 * the workspace. Both fingers move together vertically; a mostly-horizontal
 * or single-finger motion never confirms.
 *
 * The bottom-corner swipe is hard to hit reliably, so this is the easier
 * activation gesture: two fingers anywhere on screen, swipe up or down.
 * See SystemUiBottomCornerGestureMonitor for how events reach this detector.
 *
 * Coordinates are raw display coordinates (v21): getX()/getY() are
 * window-relative and a third-party launcher home screen offsets them, so
 * the detector worked in apps and never on the desktop.
 */
class TwoFingerSwipeGestureDetector(
    private val density: Float,
    private val canActivate: () -> Boolean = { true },
) {
    enum class Result { IGNORED, OBSERVING, ABANDONED, CONFIRMED }

    // A deliberate two-finger swipe. Loosened 2026-11-06 (v19): the desktop
    // swipe would not trigger at 60dp / 1.5:1 / 40dp gap, so the bar is now
    // 45dp, vertical only needs to beat horizontal 1.25:1, and the fingers
    // may drift 60dp apart — a natural swipe is rarely perfectly straight
    // or perfectly parallel. Still requires both fingers moving together
    // within 2 seconds, so a two-finger scroll does not confirm.
    // v21: 45dp -> 30dp. The user (2026-11-06 14:30) reports the activation
    // feels delayed — the workspace only opens after a long swipe. 30dp
    // confirms sooner; a two-finger scroll in an app is rare enough that
    // the vertical-dominance and gap checks still guard it.
    private val minimumDistance = 30f * density
    private var tracking = false
    private var downTime = 0L
    private var startCentroidX = 0f
    private var startCentroidY = 0f
    private var startGapY = 0f

    val isObserving: Boolean get() = tracking

    /** The last confirmed swipe, described in dp. */
    var lastConfirmed: String? = null
        private set

    private fun dp(pixels: Float) = (pixels / density).toInt()

    // Raw display coordinates for a pointer. getX()/getY() are window-relative:
    // in an app the window is full-screen so they match raw, but on a
    // third-party launcher home screen the window can be offset (status bar
    // / launcher layout), which shifted the centroid and broke the bounds
    // check — the detector worked in apps and never on the desktop. The
    // bottom-corner detector uses rawX/rawY and works on the desktop, so this
    // one now does the same. The window offset is identical for every pointer,
    // so pointer 1's raw coordinate is its getX() plus pointer 0's offset.
    private fun rawX(event: MotionEvent, pointerIndex: Int): Float =
        event.getX(pointerIndex) + (event.rawX - event.getX(0))

    private fun rawY(event: MotionEvent, pointerIndex: Int): Float =
        event.getY(pointerIndex) + (event.rawY - event.getY(0))

    fun reset() {
        tracking = false
    }

    fun observe(event: MotionEvent, screenWidth: Int, screenHeight: Int): Result {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = false
                return Result.IGNORED
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount != 2) {
                    tracking = false
                    return Result.IGNORED
                }
                if (!canActivate()) return Result.IGNORED
                // Centroid of the two fingers at the moment the second lands,
                // plus their vertical gap (they must move together, not pinch).
                // Raw display coordinates (see rawX/rawY above).
                startCentroidX = (rawX(event, 0) + rawX(event, 1)) / 2f
                startCentroidY = (rawY(event, 0) + rawY(event, 1)) / 2f
                startGapY = abs(rawY(event, 0) - rawY(event, 1))
                if (!startCentroidX.isFinite() || !startCentroidY.isFinite() ||
                    startCentroidX !in 0f..screenWidth.toFloat() ||
                    startCentroidY !in 0f..screenHeight.toFloat()
                ) {
                    tracking = false
                    return Result.IGNORED
                }
                downTime = event.eventTime
                tracking = true
                return Result.OBSERVING
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking || event.pointerCount != 2) {
                    tracking = false
                    return Result.IGNORED
                }
                if (event.eventTime - downTime !in 0L..2_000L) {
                    tracking = false
                    return Result.ABANDONED
                }
                val centroidX = (rawX(event, 0) + rawX(event, 1)) / 2f
                val centroidY = (rawY(event, 0) + rawY(event, 1)) / 2f
                val dx = centroidX - startCentroidX
                val dy = centroidY - startCentroidY
                if (!dx.isFinite() || !dy.isFinite()) {
                    tracking = false
                    return Result.ABANDONED
                }
                // Both fingers must move together: their vertical gap must
                // not change much, so a pinch/spread or two unrelated
                // touches never confirm.
                val currentGapY = abs(rawY(event, 0) - rawY(event, 1))
                if (abs(currentGapY - startGapY) > 60f * density) {
                    tracking = false
                    return Result.ABANDONED
                }
                val vertical = abs(dy)
                val horizontal = abs(dx)
                if (vertical >= minimumDistance && vertical > horizontal * 1.25f) {
                    lastConfirmed = "two-finger ${if (dy < 0) "up" else "down"} " +
                        "move=${dp(dx)},${dp(dy)} ms=${event.eventTime - downTime}"
                    tracking = false
                    return if (canActivate()) Result.CONFIRMED else Result.ABANDONED
                }
                // A mostly-horizontal two-finger move is a scroll/pinch, not
                // this gesture: give it up early so apps keep their input.
                if (horizontal >= minimumDistance && horizontal > vertical * 1.25f) {
                    tracking = false
                    return Result.ABANDONED
                }
                // Still moving but not far enough yet; keep observing.
                return Result.OBSERVING
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTracking = tracking
                tracking = false
                return if (wasTracking) Result.ABANDONED else Result.IGNORED
            }
            else -> return if (tracking) Result.OBSERVING else Result.IGNORED
        }
    }
}
