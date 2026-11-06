package io.github.jheyzo.panedeck.task

import io.github.jheyzo.panedeck.workspace.WorkspaceGeometry

interface MainTaskPresenter {
    fun present(taskId: Int, geometry: WorkspaceGeometry): Result<Unit>
    /** Bounds-laid-out presentation: WM sizes the app to the pane, leash stays identity. */
    fun presentAtBounds(taskId: Int, geometry: WorkspaceGeometry): Result<Unit>
    fun restore(taskId: Int): Result<Unit>
    fun reapply(taskId: Int, geometry: WorkspaceGeometry): Result<Unit>
}
