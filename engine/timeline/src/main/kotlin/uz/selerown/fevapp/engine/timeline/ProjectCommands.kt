package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.CanvasFormat
import uz.selerown.fevapp.core.model.Project

/**
 * Changes the output frame shape and, optionally, the background color behind
 * clips that do not fill it (Format tool). Undoable like any edit.
 */
class SetCanvasCommand(
    private val canvas: CanvasFormat,
    private val backgroundColor: Int? = null,
    private val backgroundBlur: Boolean? = null,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val color = backgroundColor ?: project.backgroundColor
        val blur = backgroundBlur ?: project.backgroundBlur
        if (project.canvas == canvas && project.backgroundColor == color && project.backgroundBlur == blur) return project
        return project.copy(canvas = canvas, backgroundColor = color, backgroundBlur = blur)
    }
}
