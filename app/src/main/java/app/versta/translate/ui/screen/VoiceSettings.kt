package app.versta.translate.ui.screen

import android.icu.text.DecimalFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalVoiceModelsMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalVoiceDownloadTask
import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.ExternalVoiceModels
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.ui.component.DownloadButton
import app.versta.translate.ui.component.LanguageBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.component.VoiceDeletionConfirmationDialog
import app.versta.translate.ui.theme.spacing

@Composable
fun VoiceSettings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    voiceViewModel: VoiceViewModel,
) {
    val voiceModels by voiceViewModel.voiceByState.collectAsStateWithLifecycle(
        ExternalVoiceModels()
    )
    val downloadTasks by voiceViewModel.downloadTasks.collectAsStateWithLifecycle()

    var voiceToBeDeleted by remember { mutableStateOf<ExternalVoiceModelDefinition?>(null) }

    val queuedTasks = (voiceModels.updates + voiceModels.available).filter { model ->
        downloadTasks.any { it.model == model }
    }
    val filteredUpdates = voiceModels.updates.filterNot { model ->
        downloadTasks.any { it.model == model }
    }
    val filteredAvailable = voiceModels.available.filterNot { model ->
        downloadTasks.any { it.model == model }
    }

    val layoutDirection = LocalLayoutDirection.current

    fun onDownload(model: ExternalVoiceModelDefinition) {
        voiceViewModel.queueDownload(model)
    }

    fun onCancel() {
        voiceViewModel.cancelDownload()
    }

    fun onClick(model: ExternalVoiceModelDefinition) {
        navigationViewModel.navigate(Screens.VoiceDetails(model.id), Screens.VoiceSettings)
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = { ScaffoldCompactBarTitle(text = stringResource(R.string.settings_voices_title)) },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            ScaffoldCompactBarEmptyActions()
        },
        actionsContentKey = "ScaffoldCompactBarEmptyActions",
        wrapContent = true
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
            Voices(
                voices = queuedTasks,
                downloadTasks = downloadTasks,
                header = { size ->
                    SettingsHeaderItem(
                        content = stringResource(R.string.downloading),
                        groupSize = size + 1, index = 0
                    )
                },
                onDownload = {
                    onDownload(it)
                },
                onCancel = {
                    onCancel()
                },
                onClick = {
                    onClick(it)
                },
                onSwipeToDelete = { language ->
                    voiceToBeDeleted = language
                }
            )

            Voices(
                voices = voiceModels.installed,
                downloadTasks = downloadTasks,
                header = { size ->
                    SettingsHeaderItem(
                        content = stringResource(R.string.installed_headline),
                        groupSize = size + 1, index = 0
                    )
                },
                onClick = {
                    onClick(it)
                },
                onSwipeToDelete = { voice ->
                    voiceToBeDeleted = voice
                }
            )

            Voices(
                voices = filteredUpdates,
                downloadTasks = downloadTasks,
                header = { size ->
                    SettingsHeaderItem(
                        content = stringResource(R.string.updates_headline),
                        groupSize = size + 1, index = 0
                    )
                },
                onDownload = {
                    onDownload(it)
                },
                onCancel = {
                    onCancel()
                },
                onClick = {
                    onClick(it)
                },
                onSwipeToDelete = { voice ->
                    voiceToBeDeleted = voice
                }
            )

            Voices(
                voices = filteredAvailable,
                downloadTasks = downloadTasks,
                header = { size ->
                    SettingsHeaderItem(
                        content = stringResource(R.string.available_headline),
                        groupSize = size + 1, index = 0
                    )
                },
                onDownload = {
                    onDownload(it)
                },
                onCancel = {
                    onCancel()
                },
                onClick = {
                    onClick(it)
                }
            )
        }

        VoiceDeletionConfirmationDialog(
            model = voiceToBeDeleted,
            onConfirmation = {
                voiceViewModel.deleteVoiceModel(it)
                voiceToBeDeleted = null
            },
            onDismissRequest = {
                voiceToBeDeleted = null
            })
    }
}

private fun LazyListScope.Voices(
    voices: List<ExternalVoiceModelDefinition>,
    header: @Composable (Int) -> Unit,
    downloadTasks: List<ExternalVoiceDownloadTask>,
    onClick: (ExternalVoiceModelDefinition) -> Unit,
    onDownload: ((ExternalVoiceModelDefinition) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSwipeToDelete: ((ExternalVoiceModelDefinition) -> Unit)? = null,
) {
    if (voices.isEmpty()) {
        return
    }

    val sizeFormat = DecimalFormat("0.00")

    item {
        header(voices.size)
    }

    items(
        count = voices.size,
        key = { voices[it].id }
    ) { it ->
        val model = remember { voices[it] }
        val task = downloadTasks.firstOrNull { it.model == model }
        val languages = model.voices.map { Language.fromIsoCode(it.language) }
            .distinctBy { it.isoCode }
            .sortedBy { it.name }

        val size = ((model.extracted ?: model.size) / 1e6)

        SettingsButtonItem(
            index = it + 1,
            groupSize = voices.size + 1,
            modifier = Modifier.animateItem(),
            colors = SettingsDefaults.colors(supportingColor = MaterialTheme.colorScheme.onSurfaceVariant),
            headlineContent = model.name,
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (model.extracted != null) ImageVector.vectorResource(R.drawable.round_save_24) else ImageVector.vectorResource(R.drawable.round_cloud_download_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredSize(MaterialTheme.spacing.medium)
                        )
                        Text(
                            text = "${sizeFormat.format(size)} MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        )
                    }
                }
            },
            underlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
                ) {
                    languages.map {
                        LanguageBadge(
                            language = it,
                            size = MaterialTheme.spacing.medium,
                        )
                    }
                }
            },
            onClick = { onClick(model) },
            trailingContent = {
                if (onDownload != null && onCancel != null) {
                    DownloadButton(
                        onClick = {
                            onDownload(model)
                        },
                        onCancel = {
                            onCancel()
                        },
                        status = task?.status ?: DownloadStatus.Idle,
                    )
                }
            },
            onSwipeToDelete = if (onSwipeToDelete != null) {
                { onSwipeToDelete(model) }
            } else {
                null
            })
    }

    ListDivider()
}

@Composable
@Preview(showBackground = true)
private fun PreviewVoicesSettings() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    VoiceSettings(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel,
        ),
        navigationViewModel = navigationViewModel,
        voiceViewModel = VoiceViewModel(
            context = LocalContext.current,
            voiceRepository = VoiceMemoryRepository(),
            externalVoiceModelsRepository = ExternalVoiceModelsMemoryRepository()
        ),
    )
}