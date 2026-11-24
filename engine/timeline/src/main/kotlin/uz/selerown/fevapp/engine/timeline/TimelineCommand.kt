package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Project

/**
 * One edit operation on a project (command pattern).
 *
 * Commands are pure functions over immutable state: they never mutate the
 * input and return the SAME instance when they do not apply (unknown clip id,
 * split too close to an edge, ...). [EditHistory] uses that to skip no-ops.
 */
fun interface TimelineCommand {
    fun apply(project: Project): Project
}
