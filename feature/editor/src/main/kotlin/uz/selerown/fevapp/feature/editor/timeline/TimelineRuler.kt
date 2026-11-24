package uz.selerown.fevapp.feature.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.common.TimeFormat
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import kotlin.math.max

/** Tick steps the ruler can use (us). The smallest that keeps labels apart wins. */
private val STEPS_US = longArrayOf(
    500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 30_000_000, 60_000_000, 300_000_000,
)

/** Time ruler drawn relative to the fixed center playhead. */
@Composable
internal fun TimelineRuler(positionUs: Long, pxPerUs: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val c = FevTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(color = c.textSecondary)
    Canvas(modifier) {
        val center = size.width / 2f
        val minGapPx = 72.dp.toPx()
        val step = STEPS_US.firstOrNull { it * pxPerUs >= minGapPx } ?: STEPS_US.last()
        val halfRangeUs = (center / pxPerUs).toLong()
        val first = max(0L, positionUs - halfRangeUs) / step * step
        val last = positionUs + halfRangeUs
        var t = first
        while (t <= last) {
            val x = center + (t - positionUs) * pxPerUs
            drawLine(c.textSecondary, Offset(x, size.height * 0.55f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            val mid = x + step / 2 * pxPerUs
            drawLine(c.border, Offset(mid, size.height * 0.75f), Offset(mid, size.height), strokeWidth = 1.dp.toPx())
            val label = if (step < 1_000_000) TimeFormat.short(t) else TimeFormat.clock(t)
            drawText(measurer, label, topLeft = Offset(x + 3.dp.toPx(), 0f), style = style)
            t += step
        }
    }
}
