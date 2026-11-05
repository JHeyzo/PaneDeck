package io.github.jheyzo.panedeck.task

import io.github.jheyzo.panedeck.workspace.WorkspaceGeometry

interface MainTaskPresenter {
    fun present(taskId: Int, geometry: WorkspaceGeometry): Result<Unit>
    fun restore(taskId: Int): Result<Unit>
    fun reapply(taskId: Int, geometry: WorkspaceGeometry): Result<Unit>
}
