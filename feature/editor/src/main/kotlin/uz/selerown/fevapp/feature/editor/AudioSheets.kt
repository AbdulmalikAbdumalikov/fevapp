package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.component.FevSecondaryButton
import uz.selerown.fevapp.core.designsystem.component.FevToolSheet
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.VoiceEffect
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/** "Audio" tool: add music from the device or the sound of a video. */
@Composable
internal fun AudioAddSheet(
    onDismiss: () -> Unit,
    onPickMusic: () -> Unit,
    onPickVideo: () -> Unit,
    onRecordVoice: () -> Unit,
) {
    SimpleSheet(R.string.tool_audio, onDismiss, onApply = onDismiss) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FevSecondaryButton(stringResource(R.string.audio_add_music), onPickMusic, Modifier.fillMaxWidth())
            FevSecondaryButton(stringResource(R.string.audio_add_from_video), onPickVideo, Modifier.fillMaxWidth())
            FevSecondaryButton(stringResource(R.string.audio_record_voice), onRecordVoice, Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.audio_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = FevTheme.colors.textSecondary,
            )
        }
    }
}

/** Volume of the selected main-track clip. */
@Composable
internal fun ClipVolumeSheet(
    initialVolume: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var volume by remember { mutableFloatStateOf(initialVolume) }
    SimpleSheet(R.string.tool_volume, onDismiss, onApply = { onApply(volume) }) {
        PercentSlider(R.string.volume_label, volume) { volume = it }
    }
}

/** Volume and fade in / fade out of the selected audio-lane clip. */
@Composable
internal fun AudioMixSheet(
    clip: AudioClip,
    onDismiss: () -> Unit,
    onApply: (volume: Float, fadeInUs: Long, fadeOutUs: Long) -> Unit,
) {
    var volume by remember { mutableFloatStateOf(clip.volume) }
    var fadeIn by remember { mutableFloatStateOf(clip.fadeInUs / 1_000_000f) }
    var fadeOut by remember { mutableFloatStateOf(clip.fadeOutUs / 1_000_000f) }
    val maxFade = (minOf(clip.durationUs / 2, AudioClip.MAX_FADE_US) / 1_000_000f).coerceAtLeast(0.1f)
    SimpleSheet(
        R.string.tool_volume,
        onDismiss,
        onApply = { onApply(volume, (fadeIn * 1_000_000).toLong(), (fadeOut * 1_000_000).toLong()) },
    ) {
        PercentSlider(R.string.volume_label, volume) { volume = it }
        SecondsSlider(R.string.fade_in, fadeIn, maxFade) { fadeIn = it }
        SecondsSlider(R.string.fade_out, fadeOut, maxFade) { fadeOut = it }
    }
}

@Composable
private fun SimpleSheet(
    @StringRes title: Int,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    content: @Composable () -> Unit,
) {
    FevToolSheet(
        title = stringResource(title),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = onApply,
    ) { content() }
}

@Composable
private fun PercentSlider(@StringRes label: Int, value: Float, onChange: (Float) -> Unit) {
    LabeledSlider(label, stringResource(R.string.value_percent, (value * 100).roundToInt()), value, 1f, onChange)
}

@Composable
private fun SecondsSlider(@StringRes label: Int, value: Float, max: Float, onChange: (Float) -> Unit) {
    LabeledSlider(label, stringResource(R.string.value_seconds, value), value.coerceAtMost(max), max, onChange)
}

@Composable
private fun LabeledSlider(@StringRes label: Int, valueText: String, value: Float, max: Float, onChange: (Float) -> Unit) {
    val c = FevTheme.colors
    Column(Modifier.padding(top = 12.dp)) {
        Row {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..max,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
    }
}

private val Voices = listOf(
    VoiceEffect.NONE to R.string.voice_none,
    VoiceEffect.HIGH to R.string.voice_high,
    VoiceEffect.LOW to R.string.voice_low,
    VoiceEffect.ROBOT to R.string.voice_robot,
    VoiceEffect.ECHO to R.string.voice_echo,
    VoiceEffect.RADIO to R.string.voice_radio,
)

/** Voice effect of the selected clip's sound. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceSheet(
    current: VoiceEffect,
    onDismiss: () -> Unit,
    onApply: (VoiceEffect) -> Unit,
) {
    var voice by remember { mutableStateOf(current) }
    SimpleSheet(R.string.tool_voice, onDismiss, onApply = { onApply(voice) }) {
        Column(Modifier.padding(vertical = 8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Voices.forEach { (effect, label) ->
                    ChoiceChip(stringResource(label), voice == effect) { voice = effect }
                }
            }
            Text(
                stringResource(R.string.voice_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = FevTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
