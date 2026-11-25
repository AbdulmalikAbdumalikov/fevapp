package uz.selerown.fevapp.engine.render

import uz.selerown.fevapp.core.model.Project

/** User choices on the Export screen. */
data class ExportSettings(
    /** Short side in pixels: 720 or 1080. */
    val resolution: Int = 1080,
    val frameRate: Int = 30,
)

/** Output frame size in pixels (always even numbers, required by encoders). */
data class OutputSize(val width: Int, val height: Int)

internal object OutputSizeCalculator {

    fun calculate(project: Project, settings: ExportSettings): OutputSize {
        val (rw, rh) = project.frameRatio()
        val short = settings.resolution
        return if (rw >= rh) {
            OutputSize(even(short * rw.toDouble() / rh), even(short.toDouble()))
        } else {
            OutputSize(even(short.toDouble()), even(short * rh.toDouble() / rw))
        }
    }

    private fun even(value: Double): Int = (value.toInt() / 2) * 2
}
