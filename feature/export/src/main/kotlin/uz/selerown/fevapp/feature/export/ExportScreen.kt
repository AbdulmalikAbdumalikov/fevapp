package uz.selerown.fevapp.feature.export

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.selerown.fevapp.core.designsystem.component.FevIconButton
import uz.selerown.fevapp.core.designsystem.component.FevPrimaryButton
import uz.selerown.fevapp.core.designsystem.component.FevSecondaryButton
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

@Composable
internal fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val running = state.phase is ExportPhase.Running

    // Android 9 and older need storage permission to write to the gallery.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start()
    }
    // Android 13+: ask once for the progress notification; export runs either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }
    val start = {
        when {
            needsLegacyPermission(context) -> permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            needsNotificationPermission(context) -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> viewModel.start()
        }
    }

    KeepScreenOn(running)

    // After saving, open the share sheet once automatically.
    val done = state.phase as? ExportPhase.Done
    LaunchedEffect(done?.uri) { done?.let { share(context, it.uri) } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = FevDimens.ScreenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FevIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(R.string.export_back),
                // Leaving does NOT stop a running export (it continues in the background).
                onClick = onBack,
            )
            Text(
                stringResource(R.string.export_title),
                style = MaterialTheme.typography.titleLarge,
                color = FevTheme.colors.textPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        when (val phase = state.phase) {
            ExportPhase.Idle -> SettingsContent(
                resolution = state.settings.resolution,
                frameRate = state.settings.frameRate,
                onResolution = viewModel::setResolution,
                onFrameRate = viewModel::setFrameRate,
                onStart = start,
            )
            is ExportPhase.Running -> StatusContent {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { phase.progress / 100f },
                        color = FevTheme.colors.accent,
                        trackColor = FevTheme.colors.border,
                        strokeWidth = 8.dp,
                        modifier = Modifier.size(140.dp),
                    )
                    Text("${phase.progress}%", style = MaterialTheme.typography.headlineMedium, color = FevTheme.colors.textPrimary)
                }
                StatusText(stringResource(R.string.export_running), stringResource(R.string.export_keep_open))
                FevSecondaryButton(stringResource(R.string.export_cancel), viewModel::cancel)
            }
            is ExportPhase.Done -> StatusContent {
                Icon(Icons.Rounded.CheckCircle, null, tint = FevTheme.colors.accent, modifier = Modifier.size(96.dp))
                StatusText(stringResource(R.string.export_done), null)
                FevPrimaryButton(stringResource(R.string.export_share), { share(context, phase.uri) }, Modifier.fillMaxWidth())
                FevSecondaryButton(stringResource(R.string.export_finish), onBack, Modifier.fillMaxWidth())
            }
            ExportPhase.Failed -> StatusContent {
                Icon(Icons.Rounded.ErrorOutline, null, tint = FevTheme.colors.danger, modifier = Modifier.size(96.dp))
                StatusText(stringResource(R.string.export_failed), null)
                FevPrimaryButton(stringResource(R.string.export_retry), viewModel::reset, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsContent(
    resolution: Int,
    frameRate: Int,
    onResolution: (Int) -> Unit,
    onFrameRate: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionGroup(
            title = stringResource(R.string.export_quality),
            options = listOf(720, 1080),
            selected = resolution,
            label = { stringResource(R.string.export_resolution_value, it) },
            onSelect = onResolution,
        )
        OptionGroup(
            title = stringResource(R.string.export_fps),
            options = listOf(30, 60),
            selected = frameRate,
            label = { stringResource(R.string.export_fps_value, it) },
            onSelect = onFrameRate,
        )
        Text(
            stringResource(R.string.export_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = FevTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        FevPrimaryButton(stringResource(R.string.export_start), onStart, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OptionGroup(
    title: String,
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = FevTheme.colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.titleSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FevTheme.colors.accent,
                    selectedLabelColor = FevTheme.colors.onAccent,
                ),
                modifier = Modifier.height(FevDimens.MinTouch),
            )
        }
    }
}

@Composable
private fun StatusContent(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) { content() }
}

@Composable
private fun StatusText(title: String, subtitle: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = FevTheme.colors.textPrimary, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FevTheme.colors.textSecondary)
        }
    }
}

/** Keeps the display awake while exporting. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private fun needsLegacyPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun share(context: Context, uri: Uri) {
    val title = context.getString(R.string.export_share_title)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData gives the share sheet a proper preview/name instead of the raw MediaStore id.
        clipData = ClipData.newUri(context.contentResolver, "Fevapp", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}
