package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.CanvasFormat
import uz.selerown.fevapp.core.model.Project

/** Changes the output frame shape (Format tool). Undoable like any edit. */
class SetCanvasCommand(private val canvas: CanvasFormat) : TimelineCommand {
    override fun apply(project: Project): Project =
        if (project.canvas == canvas) project else project.copy(canvas = canvas)
}
