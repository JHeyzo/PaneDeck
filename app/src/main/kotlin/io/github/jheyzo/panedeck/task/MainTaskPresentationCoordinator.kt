package io.github.jheyzo.panedeck.task

import android.content.Context
import android.graphics.Rect
import android.view.Display
import io.github.jheyzo.panedeck.framework.ActivityTaskManagerCompat
import io.github.jheyzo.panedeck.framework.TaskSurfaceCompat
import io.github.jheyzo.panedeck.framework.WindowContainerTransactionCompat
import io.github.jheyzo.panedeck.logging.NeXtepLog
import io.github.jheyzo.panedeck.trigger.TriggerBroadcastContract
import io.github.jheyzo.panedeck.workspace.WorkspaceGeometry

class MainTaskPresentationCoordinator(context: Context) {
    private val applicationContext = context.applicationContext ?: context
    private val taskRepository = TaskRepository(applicationContext)
    private val surfacePresenter = SurfaceTaskPresenter()
    private var presentedTaskId: Int? = null
    private var activePresenter: MainTaskPresenter? = null
    var onHomePresented: (() -> Unit)? = null
    /** Invoked when the foreground becomes HOME from a presented app (user
     * swiped up to go home): the workspace exits instead of keeping HOME
     * as a scaled main window. */
    var onHomeFromApp: (() -> Unit)? = null
    private var homePresented = false
    /** The main task currently laid out at the pane rect (bounds mode); null when the
     * main task uses the leash path (HOME) or none is presented. */
    private var boundsTaskId: Int? = null

    fun presentForeground(geometry: WorkspaceGeometry, animateFromFullscreen: Boolean = false): Result<Unit> {
        val task = taskRepository.foregroundTask()
            ?: return Result.failure(IllegalStateException("No foreground Display 0 task"))
        return presentTask(task, geometry, animateFromFullscreen)
    }

    fun presentTask(taskId: Int, geometry: WorkspaceGeometry): Result<Unit> {
        val task = taskRepository.findTask(taskId)
            ?: return Result.failure(IllegalStateException("Task $taskId is unavailable"))
        return presentTask(task, geometry)
    }

    private fun presentTask(
        task: TaskRepository.TaskSnapshot,
        geometry: WorkspaceGeometry,
        animateFromFullscreen: Boolean = false,
    ): Result<Unit> {
        if (!isMainFullscreen(task)) {
            // Native freeform/PiP/split-screen surfaces belong to WM Shell, not our
            // fullscreen presentation. Do not replace their position or rotation matrix.
            return restoreForMove(task.taskId)
        }
        return presentTask(task, geometry, TriggerBroadcastContract.resolveHomePackage(applicationContext), animateFromFullscreen)
    }

    private fun presentTask(
        task: TaskRepository.TaskSnapshot,
        geometry: WorkspaceGeometry,
        homePackage: String?,
        animateFromFullscreen: Boolean = false,
    ): Result<Unit> {
        if (task.component.packageName == TriggerBroadcastContract.SYSTEM_UI_PACKAGE) {
            return Result.failure(
                IllegalStateException("Unsafe foreground task ${task.component.packageName}"),
            )
        }
        // HOME used to be skipped here ("Launcher owns transform"), so the
        // default window stayed a square fullscreen home screen while opened
        // apps got rounded corners. Present it like any other main task so the
        // default window is fitted and rounded the same way.
        if (presentedTaskId == task.taskId && activePresenter === surfacePresenter) {
            // Leash path (v15 revert): reapply re-fits the leash scale to
            // the pane. A task left over from a bounds-mode version may
            // still carry pane bounds; clear them so it can go fullscreen
            // again and the leash (not bounds) owns its size.
            if (boundsTaskId == task.taskId) {
                boundsTaskId = null
                clearFullscreenBounds(task).getOrThrow()
            }
            return surfacePresenter.reapply(task.taskId, geometry)
        }

        // Fullscreen tasks inherit display bounds. Explicit screen-sized bounds survive
        // rotation and can leave a portrait Activity confined to the old landscape area.
        homePresented = false
        val isHome = task.component.packageName == homePackage
        val surfaceResult = if (isHome) {
            // HOME keeps the leash path: the launcher scales itself and its
            // touch remap assumes the leash transform. Resizing it to the pane
            // would double-correct taps.
            boundsTaskId = null
            clearFullscreenBounds(task).mapCatching {
                if (animateFromFullscreen) surfacePresenter.presentFromFullscreen(task.taskId, geometry).getOrThrow()
                else surfacePresenter.present(task.taskId, geometry).getOrThrow()
            }
        } else {
            // REVERTED (v15, 2026-11-06): bounds mode left every app
            // half-screen (v12/v13/v14 all failed to fix it: the task
            // bounds + resizeable flag do not make ColorOS apps fill the
            // pane rect). Back to the leash path (v11 behavior) so apps
            // fill the pane again; the IME/tap offset that bounds mode
            // was meant to fix is the lesser problem and returns with it.
            // Clear any pane bounds a previous version set.
            boundsTaskId = null
            clearFullscreenBounds(task).mapCatching {
                if (animateFromFullscreen) surfacePresenter.presentFromFullscreen(task.taskId, geometry).getOrThrow()
                else surfacePresenter.present(task.taskId, geometry).getOrThrow()
            }
        }
        if (surfaceResult.isSuccess) {
            val previousTaskId = presentedTaskId
            val previousPresenter = activePresenter
            presentedTaskId = task.taskId
            activePresenter = surfacePresenter
            if (previousTaskId != null && previousTaskId != task.taskId) {
                releaseTask(previousTaskId, previousPresenter)
            }
            if (isHome) notifyHomePresented()
        }
        return surfaceResult
    }

    /** Release a previously presented task, returning a bounds-laid-out one to fullscreen first. */
    private fun releaseTask(taskId: Int, presenter: MainTaskPresenter?) {
        clearBoundsForRestore(taskId)
        presenter?.restore(taskId)?.onFailure {
            NeXtepLog.warn("main_task_presenter", "Previous task release failed taskId=$taskId", it)
        }
    }

    /** Lay an app out at the main pane rect so it draws (and takes input) at pane size. */
    private fun applyMainBounds(
        task: TaskRepository.TaskSnapshot,
        geometry: WorkspaceGeometry,
    ): Result<Unit> {
        if (task.vendorWindowed || task.displayId != Display.DEFAULT_DISPLAY) return Result.success(Unit)
        val bounds = Rect(geometry.contentLeft, geometry.contentTop,
            geometry.contentRight, geometry.contentBottom)
        // Bounds alone do not resize the app: without the resizeable flag
        // the window keeps its old size and the rest of the pane stays
        // black. This hits every app, not just QQ. Set it BEFORE the
        // bounds-equality early return: a task whose bounds were already
        // set (e.g. by v12, without the flag) would otherwise never get
        // the flag. Slots already do this.
        ActivityTaskManagerCompat.setTaskResizeable(
            task.taskId, ActivityTaskManagerCompat.TASK_RESIZE_MODE_RESIZEABLE,
        ).onFailure { NeXtepLog.warn("main_task_bounds", "setTaskResizeable failed taskId=${task.taskId}", it) }
        if (task.bounds == bounds) return Result.success(Unit)
        NeXtepLog.info("main_task_bounds", "Laying out taskId=${task.taskId} at $bounds (pane rect)")
        return task.token?.let { token ->
            WindowContainerTransactionCompat.applyTaskBounds(token, bounds)
        } ?: ActivityTaskManagerCompat.resizeTask(task.taskId, bounds)
    }

    /** Clear a bounds-mode task's pane bounds so it returns to fullscreen on release. */
    private fun clearBoundsForRestore(taskId: Int) {
        if (boundsTaskId != taskId) return
        boundsTaskId = null
        val task = taskRepository.findTask(taskId) ?: return
        clearFullscreenBounds(task)
    }

    fun reconcileForeground(geometry: WorkspaceGeometry): Result<Unit> =
        reconcileForeground(geometry, taskRepository.runningTasks())

    fun reconcileForeground(
        geometry: WorkspaceGeometry,
        tasks: List<TaskRepository.TaskSnapshot>,
    ): Result<Unit> = runCatching {
        val foreground = tasks.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
            ?: error("No foreground Display 0 task")
        val homePackage = TriggerBroadcastContract.resolveHomePackage(applicationContext)
        if (foreground.component.packageName == homePackage) {
            // Swipe-up to go home from a presented app exits the workspace
            // instead of keeping HOME as a scaled main window. When the
            // workspace opened on HOME (or HOME is already the main task),
            // HOME stays the main task. Posted, not synchronous: exiting
            // from inside reconcile would re-enter the controller's state
            // machine while it is still reconciling.
            val previousTask = presentedTaskId?.let { id -> tasks.firstOrNull { it.taskId == id } }
            val cameFromApp = previousTask != null &&
                previousTask.component.packageName != homePackage
            if (cameFromApp) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onHomeFromApp?.invoke()
                }
                return@runCatching
            }
            // HOME is a presentable main task now (see presentTask): fit and
            // round it like an app instead of restoring it to fullscreen.
            // presentTask restores any previously presented app on its own.
            presentTask(foreground, geometry, homePackage).getOrThrow()
            notifyHomePresented()
            return@runCatching
        }
        if (foreground.component.packageName == TriggerBroadcastContract.SYSTEM_UI_PACKAGE) {
            // Swipe-up-and-hold opened recents (SystemUI) instead of HOME:
            // the gesture never lands on the home package, so the HOME
            // branch above never fires. Treat recents appearing while a
            // non-HOME app was presented as the same exit signal, so the
            // workspace closes and the recents the user sees are global.
            // Posted, not synchronous: exiting from inside reconcile would
            // re-enter the controller's state machine while it is still
            // reconciling.
            val previousTask = presentedTaskId?.let { id -> tasks.firstOrNull { it.taskId == id } }
            val homePackageName = TriggerBroadcastContract.resolveHomePackage(applicationContext)
            val cameFromApp = previousTask != null &&
                previousTask.component.packageName != homePackageName
            if (cameFromApp) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onHomeFromApp?.invoke()
                }
            }
            return@runCatching
        }
        if (!isMainFullscreen(foreground)) {
            // Keep a different fullscreen task fitted behind the native floating window.
            // If the presented task itself became freeform, release our ownership.
            presentedTaskId?.let { taskId ->
                if (tasks.firstOrNull { it.taskId == taskId }?.let(::isMainFullscreen) != true) {
                    restoreForeground().getOrThrow()
                }
            }
            return@runCatching
        }
        val current = presentedTaskId
        if (current != null && current != foreground.taskId) {
            restoreForeground().getOrThrow()
        }
        if (presentedTaskId == foreground.taskId) {
            // Bounds-laid-out apps move with the pane rect on geometry change
            // (rotation); the leash stays identity, so reapply only refreshes it.
            if (boundsTaskId == foreground.taskId) {
                applyMainBounds(foreground, geometry).getOrThrow()
            }
            activePresenter?.reapply(foreground.taskId, geometry)
                ?.getOrThrow()
                ?: error("Main presenter state is incomplete")
        } else {
            presentTask(foreground, geometry, homePackage).getOrThrow()
        }
    }

    fun restoreForMove(taskId: Int): Result<Unit> {
        if (presentedTaskId != taskId) return Result.success(Unit)
        return restoreForeground()
    }

    fun currentTaskId(): Int? = presentedTaskId

    fun restoreForeground(animateToFullscreen: Boolean = false): Result<Unit> {
        val taskId = presentedTaskId ?: return Result.success(Unit)
        val presenter = activePresenter ?: return Result.success(Unit)
        // Bounds-laid-out apps must return to fullscreen; the leash path's
        // entry cleared bounds already, so its release starts no layout change.
        clearBoundsForRestore(taskId)
        val restored = if (animateToFullscreen && presenter === surfacePresenter) {
            surfacePresenter.restoreToFullscreen(taskId)
        } else presenter.restore(taskId)
        return restored.mapCatching {
            if (presentedTaskId == taskId && activePresenter === presenter) {
                presentedTaskId = null
                activePresenter = null
            }
        }
    }

    private fun notifyHomePresented() {
        if (homePresented) return
        homePresented = true
        onHomePresented?.invoke()
    }

    private fun clearFullscreenBounds(
        task: TaskRepository.TaskSnapshot,
    ): Result<Unit> {
        // The display-move owner already cleared the fullscreen override during
        // layout restoration. Do not request a second layout after the move.
        if (TaskSurfaceCompat.hasPreparedPromotion(task.taskId)) return Result.success(Unit)
        val fullscreenMode = ActivityTaskManagerCompat
            .resolveWindowingMode("WINDOWING_MODE_FULLSCREEN")
            ?: WINDOWING_MODE_FULLSCREEN
        if (task.vendorWindowed || task.displayId != Display.DEFAULT_DISPLAY || task.windowingMode != fullscreenMode) {
            return Result.success(Unit)
        }

        NeXtepLog.info(
            "main_task_bounds",
            "Clearing fullscreen bounds override taskId=${task.taskId}",
        )
        // Empty bounds remove the override, including when the resolved bounds already
        // equal the display. Comparing resolved bounds cannot detect a stale override.
        return task.token?.let { token ->
            WindowContainerTransactionCompat.applyTaskBounds(token, Rect())
        } ?: ActivityTaskManagerCompat.resizeTask(task.taskId, Rect())
    }

    private companion object {
        const val WINDOWING_MODE_FULLSCREEN = 1
    }

    private fun isMainFullscreen(task: TaskRepository.TaskSnapshot): Boolean =
        !task.vendorWindowed && task.displayId == Display.DEFAULT_DISPLAY &&
            task.windowingMode == WINDOWING_MODE_FULLSCREEN

}
