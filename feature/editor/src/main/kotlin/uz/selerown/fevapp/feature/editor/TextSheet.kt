package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.component.FevToolSheet
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.TextAnimation
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextFont
import uz.selerown.fevapp.core.model.TextStyle

/**
 * Colors a user can give their text. These are CONTENT colors (they end up in
 * the video), not UI colors, so they live here and not in FevColors.
 */
internal val ContentPalette: List<Pair<Int, Int>> = listOf(
    0xFFFFFFFF.toInt() to R.string.color_white,
    0xFF111214.toInt() to R.string.color_black,
    0xFFFFD54F.toInt() to R.string.color_yellow,
    0xFFF87171.toInt() to R.string.color_red,
    0xFF4ADE80.toInt() to R.string.color_green,
    0xFF6EA8FE.toInt() to R.string.color_blue,
    0xFFF9A8D4.toInt() to R.string.color_pink,
)

private val Positions = listOf(
    0.15f to R.string.position_top,
    0.5f to R.string.position_middle,
    0.8f to R.string.position_bottom,
)

private val Animations = listOf(
    TextAnimation.NONE to R.string.anim_none,
    TextAnimation.FADE to R.string.anim_fade,
    TextAnimation.POP to R.string.anim_pop,
    TextAnimation.SLIDE_UP to R.string.anim_slide,
)

private val Fonts = listOf(
    TextFont.SANS to R.string.font_sans,
    TextFont.SERIF to R.string.font_serif,
    TextFont.MONO to R.string.font_mono,
)

/** Add a new text ([initial] = null) or edit an existing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TextSheet(
    initial: TextClip?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (text: String, style: TextStyle) -> Unit,
) {
    val c = FevTheme.colors
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var style by remember { mutableStateOf(initial?.style ?: TextStyle()) }

    FevToolSheet(
        title = title,
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { onSave(text, style) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.text_hint)) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    focusedLabelColor = c.accent,
                    cursorColor = c.accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SheetSection(R.string.text_size)
            Slider(
                value = style.sizeFraction,
                onValueChange = { style = style.copy(sizeFraction = it) },
                valueRange = TextStyle.MIN_SIZE..TextStyle.MAX_SIZE,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
            )

            SheetSection(R.string.text_color)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentPalette.forEach { (argb, name) ->
                    ColorSwatch(argb, stringResource(name), selected = style.color == argb) {
                        style = style.copy(color = argb)
                    }
                }
            }

            SheetSection(R.string.text_style)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.text_background), style.background) {
                    style = style.copy(background = !style.background)
                }
                ChoiceChip(stringResource(R.string.text_bold), style.bold) { style = style.copy(bold = !style.bold) }
            }

            SheetSection(R.string.text_font)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fonts.forEach { (font, label) ->
                    ChoiceChip(stringResource(label), style.font == font) { style = style.copy(font = font) }
                }
            }

            SheetSection(R.string.text_animation)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Animations.forEach { (anim, label) ->
                    ChoiceChip(stringResource(label), style.animation == anim) { style = style.copy(animation = anim) }
                }
            }

            SheetSection(R.string.text_position)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Positions.forEach { (y, label) ->
                    ChoiceChip(stringResource(label), style.positionY == y) { style = style.copy(positionY = y) }
                }
            }
        }
    }
}

@Composable
internal fun SheetSection(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = FevTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Accent filter chip at the minimum touch height. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FevTheme.colors.accent,
            selectedLabelColor = FevTheme.colors.onAccent,
        ),
        modifier = Modifier.height(FevDimens.MinTouch),
    )
}

@Composable
internal fun ColorSwatch(argb: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val c = FevTheme.colors
    Box(
        Modifier
            .size(FevDimens.MinTouch)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.border, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color(argb)),
    )
}
