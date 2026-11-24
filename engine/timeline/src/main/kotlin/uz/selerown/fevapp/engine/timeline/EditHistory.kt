package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Project

/**
 * Undo/redo for a project.
 *
 * Edits go through [execute] as [TimelineCommand]s. Because projects are
 * immutable, the history simply keeps the previous snapshots (memento);
 * structural sharing of unchanged lists keeps this cheap.
 */
class EditHistory(
    initial: Project,
    private val maxSteps: Int = 200,
) {
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    var current: Project = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Applies [command]. Returns false (and records nothing) for no-ops. */
    fun execute(command: TimelineCommand): Boolean {
        val next = command.apply(current)
        if (next === current || next == current) return false
        undoStack.addLast(current)
        if (undoStack.size > maxSteps) undoStack.removeFirst()
        redoStack.clear()
        current = next
        return true
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(current)
        current = previous
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(current)
        current = next
        return true
    }

    /**
     * Replaces the current state WITHOUT an undo step (e.g. renaming or
     * metadata updates that should not be undoable).
     */
    fun replaceCurrent(project: Project) {
        current = project
    }
}
