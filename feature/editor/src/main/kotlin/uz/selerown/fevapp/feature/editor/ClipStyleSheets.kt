package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import uz.selerown.fevapp.core.designsystem.component.FevPrimaryButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.common.TimeFormat
import uz.selerown.fevapp.core.designsystem.component.FevToolSheet
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.Keyframe
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.designsystem.component.FevSecondaryButton
import uz.selerown.fevapp.core.model.TransitionType
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f)

/** Constant speed of the selected video clip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeedSheet(clip: Clip, onDismiss: () -> Unit, onApply: (Float) -> Unit) {
    val c = FevTheme.colors
    var speed by remember { mutableFloatStateOf(clip.speed) }
    FevToolSheet(
        title = stringResource(R.string.tool_speed),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onApply(speed) },
    ) {
        Row(Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(R.string.speed_value, formatSpeed(speed)),
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.speed_duration, TimeFormat.short((clip.sourceLengthUs / speed).toLong())),
                style = MaterialTheme.typography.titleSmall,
                color = c.textSecondary,
            )
        }
        Slider(
            value = speed,
            onValueChange = { speed = (it * 20).roundToInt() / 20f }, // 0.05 steps
            valueRange = Clip.MIN_SPEED..Clip.MAX_SPEED,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedPresets.forEach { preset ->
                ChoiceChip(stringResource(R.string.speed_value, formatSpeed(preset)), abs(speed - preset) < 0.01f) {
                    speed = preset
                }
            }
        }
        Text(
            stringResource(R.string.speed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')

private val Looks = listOf(
    ClipLook.NONE to R.string.look_none,
    ClipLook.MONO to R.string.look_mono,
    ClipLook.NOIR to R.string.look_noir,
    ClipLook.WARM to R.string.look_warm,
    ClipLook.COOL to R.string.look_cool,
    ClipLook.VIVID to R.string.look_vivid,
    ClipLook.FADED to R.string.look_faded,
)

/**
 * Looks + manual adjustments for the selected clip. Every change is shown
 * live in the preview ([onPreview]); ✓ saves, ✕ throws it away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    clip: Clip,
    onPreview: (ClipLook, ColorAdjust) -> Unit,
    onDismiss: () -> Unit,
    onApply: (ClipLook, ColorAdjust, applyToAll: Boolean) -> Unit,
) {
    var look by remember { mutableStateOf(clip.look) }
    var color by remember { mutableStateOf(clip.color) }
    var applyToAll by remember { mutableStateOf(false) }
    LaunchedEffect(look, color) { onPreview(look, color) }

    FevToolSheet(
        title = stringResource(R.string.tool_filter),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onApply(look, color, applyToAll) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetSection(R.string.filter_looks)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Looks.forEach { (option, label) ->
                    ChoiceChip(stringResource(label), look == option) { look = option }
                }
            }
            SheetSection(R.string.filter_adjust)
            AdjustSlider(R.string.adjust_brightness, color.brightness) { color = color.copy(brightness = it) }
            AdjustSlider(R.string.adjust_contrast, color.contrast) { color = color.copy(contrast = it) }
            AdjustSlider(R.string.adjust_saturation, color.saturation) { color = color.copy(saturation = it) }
            AdjustSlider(R.string.adjust_warmth, color.warmth) { color = color.copy(warmth = it) }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.filter_reset), false) {
                    look = ClipLook.NONE
                    color = ColorAdjust()
                }
                ChoiceChip(stringResource(R.string.filter_apply_all), applyToAll) { applyToAll = !applyToAll }
            }
        }
    }
}

@Composable
private fun AdjustSlider(@StringRes label: Int, value: Float, onChange: (Float) -> Unit) {
    val c = FevTheme.colors
    Column {
        Row {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.value_signed_percent, (value * 100).roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
        }
        Slider(
            value = value,
            onValueChange = { onChange((it * 100).roundToInt() / 100f) },
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
    }
}

private val Transitions = listOf(
    TransitionType.NONE to R.string.transition_none,
    TransitionType.FADE_BLACK to R.string.transition_black,
    TransitionType.FADE_WHITE to R.string.transition_white,
)

/** Transition from the selected clip into the next one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransitionSheet(
    clip: Clip,
    onDismiss: () -> Unit,
    onApply: (TransitionType, Long, applyToAll: Boolean) -> Unit,
) {
    val c = FevTheme.colors
    var type by remember { mutableStateOf(clip.transition) }
    var seconds by remember { mutableFloatStateOf(clip.transitionUs / 1_000_000f) }
    var applyToAll by remember { mutableStateOf(false) }
    FevToolSheet(
        title = stringResource(R.string.tool_transition),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onApply(type, (seconds * 1_000_000).toLong(), applyToAll) },
    ) {
        SheetSection(R.string.transition_type)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Transitions.forEach { (option, label) ->
                ChoiceChip(stringResource(label), type == option) { type = option }
            }
        }
        Row(Modifier.padding(top = 16.dp)) {
            Text(
                stringResource(R.string.transition_length),
                style = MaterialTheme.typography.titleSmall,
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.value_seconds, seconds), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        Slider(
            value = seconds,
            onValueChange = { seconds = (it * 10).roundToInt() / 10f },
            valueRange = Clip.MIN_TRANSITION_US / 1_000_000f..Clip.MAX_TRANSITION_US / 1_000_000f,
            enabled = type != TransitionType.NONE,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        ChoiceChip(stringResource(R.string.filter_apply_all), applyToAll) { applyToAll = !applyToAll }
        Text(
            stringResource(R.string.transition_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Position and size of a picture-in-picture clip (button alternative to dragging). */
@Composable
internal fun OverlaySheet(
    clip: OverlayClip,
    onDismiss: () -> Unit,
    onApply: (centerX: Float, centerY: Float, scale: Float) -> Unit,
) {
    var x by remember { mutableFloatStateOf(clip.centerX) }
    var y by remember { mutableFloatStateOf(clip.centerY) }
    var scale by remember { mutableFloatStateOf(clip.scale) }
    FevToolSheet(
        title = stringResource(R.string.tool_overlay_transform),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onApply(x, y, scale) },
    ) {
        FractionSlider(R.string.overlay_size, scale, OverlayClip.MIN_SCALE..OverlayClip.MAX_SCALE) { scale = it }
        FractionSlider(R.string.overlay_x, x, 0f..1f) { x = it }
        FractionSlider(R.string.overlay_y, y, 0f..1f) { y = it }
        Text(
            stringResource(R.string.overlay_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = FevTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun FractionSlider(@StringRes label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val c = FevTheme.colors
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.value_percent, (value * 100).roundToInt()), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        Slider(
            value = value,
            onValueChange = { onChange((it * 100).roundToInt() / 100f) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
    }
}

/**
 * Keyframe at the playhead for the selected text/overlay: position, size and
 * opacity sliders (the accessible alternative to dragging on the preview).
 */
@Composable
internal fun KeyframeSheet(
    state: Keyframe,
    scaleRange: ClosedFloatingPointRange<Float>,
    count: Int,
    hasHere: Boolean,
    onDismiss: () -> Unit,
    onSave: (x: Float, y: Float, scale: Float, alpha: Float) -> Unit,
    onDelete: () -> Unit,
) {
    var x by remember { mutableFloatStateOf(state.x) }
    var y by remember { mutableFloatStateOf(state.y) }
    var scale by remember { mutableFloatStateOf(state.scale.coerceIn(scaleRange)) }
    var alpha by remember { mutableFloatStateOf(state.alpha) }
    FevToolSheet(
        title = stringResource(R.string.tool_keyframe),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onSave(x, y, scale, alpha) },
    ) {
        Text(
            stringResource(if (hasHere) R.string.keyframe_here else R.string.keyframe_new, count),
            style = MaterialTheme.typography.bodyMedium,
            color = FevTheme.colors.textSecondary,
        )
        FractionSlider(R.string.overlay_size, scale, scaleRange) { scale = it }
        FractionSlider(R.string.overlay_x, x, 0f..1f) { x = it }
        FractionSlider(R.string.overlay_y, y, 0f..1f) { y = it }
        FractionSlider(R.string.keyframe_opacity, alpha, 0f..1f) { alpha = it }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FevPrimaryButton(stringResource(R.string.keyframe_save), { onSave(x, y, scale, alpha) }, Modifier.fillMaxWidth())
            if (hasHere) FevSecondaryButton(stringResource(R.string.keyframe_delete), onDelete, Modifier.fillMaxWidth())
        }
        Text(
            stringResource(R.string.keyframe_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = FevTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
