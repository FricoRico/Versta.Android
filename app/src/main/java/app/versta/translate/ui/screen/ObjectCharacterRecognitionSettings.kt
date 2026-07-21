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
import app.versta.translate.adapter.outbound.ExternalObjectCharacterRecognitionModelsMemoryRepository
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionRepositoryMemoryRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionDownloadTask
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ObjectCharacterRecognitionViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.DownloadButton
import app.versta.translate.ui.component.LanguageBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ModelDeletionConfirmationDialog
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.theme.spacing

@Composable
fun ObjectCharacterRecognitionSettings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    objectCharacterRecognitionViewModel: ObjectCharacterRecognitionViewModel,
) {
    val ocrModels by objectCharacterRecognitionViewModel.ocrModelsByState.collectAsStateWithLifecycle(
        ExternalObjectCharacterRecognitionModels()
    )
    val downloadTasks by objectCharacterRecognitionViewModel.downloadTasks.collectAsStateWithLifecycle()

    var ocrToBeDeleted by remember { mutableStateOf<String?>(null) }

    val queuedTasks = (ocrModels.updates + ocrModels.available).filter { model ->
        downloadTasks.any { it.model == model.definition }
    }
    val filteredUpdates = ocrModels.updates.filterNot { model ->
        downloadTasks.any { it.model == model.definition }
    }
    val filteredAvailable = ocrModels.available.filterNot { model ->
        downloadTasks.any { it.model == model.definition }
    }

    val layoutDirection = LocalLayoutDirection.current

    fun onDownload(model: ExternalObjectCharacterRecognitionModelDefinition) {
        objectCharacterRecognitionViewModel.queueDownload(model)
    }

    fun onCancel() {
        objectCharacterRecognitionViewModel.cancelDownload()
    }

    fun onClick(model: ExternalObjectCharacterRecognitionModelDefinition) {
        navigationViewModel.navigate(
            Screens.ObjectCharacterRecognitionDetails(model.id),
            Screens.ObjectCharacterRecognitionSettings
        )
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = { ScaffoldCompactBarTitle(text = stringResource(R.string.settings_ocr_title)) },
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
            OcrModels(
                models = queuedTasks,
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
                onSwipeToDelete = { model ->
                    ocrToBeDeleted = model.id
                }
            )

            OcrModels(
                models = ocrModels.installed,
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
                onSwipeToDelete = { model ->
                    ocrToBeDeleted = model.id
                }
            )

            OcrModels(
                models = filteredUpdates,
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
                onSwipeToDelete = { model ->
                    ocrToBeDeleted = model.id
                }
            )

            OcrModels(
                models = filteredAvailable,
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

        ModelDeletionConfirmationDialog(
            model = ocrToBeDeleted,
            titleRes = R.string.delete_ocr_model_title,
            descriptionRes = R.string.delete_ocr_model_description,
            onConfirmation = {
                objectCharacterRecognitionViewModel.deleteOcrModel(it)
                ocrToBeDeleted = null
            },
            onDismissRequest = {
                ocrToBeDeleted = null
            })
    }
}

private fun LazyListScope.OcrModels(
    models: List<app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelWithState>,
    header: @Composable (Int) -> Unit,
    downloadTasks: List<ExternalObjectCharacterRecognitionDownloadTask>,
    onClick: (ExternalObjectCharacterRecognitionModelDefinition) -> Unit,
    onDownload: ((ExternalObjectCharacterRecognitionModelDefinition) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSwipeToDelete: ((ExternalObjectCharacterRecognitionModelDefinition) -> Unit)? = null,
) {
    if (models.isEmpty()) {
        return
    }

    val sizeFormat = DecimalFormat("0.00")

    item {
        header(models.size)
    }

    items(
        count = models.size,
        key = { models[it].definition.id }
    ) { it ->
        val modelWithState = remember(models[it]) { models[it] }
        val model = modelWithState.definition
        val task = downloadTasks.firstOrNull { it.model == model }
        val languages = model.languages.map { Language.fromIsoCode(it) }
            .distinctBy { it.isoCode }
            .sortedBy { it.name }

        val size = ((modelWithState.extracted ?: model.size) / 1e6)

        SettingsButtonItem(
            index = it + 1,
            groupSize = models.size + 1,
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
                            if (modelWithState.extracted != null) ImageVector.vectorResource(R.drawable.round_save_24) else ImageVector.vectorResource(R.drawable.round_cloud_download_24),
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
                    languages.take(5).forEach {
                        LanguageBadge(
                            language = it,
                            size = MaterialTheme.spacing.medium,
                        )
                    }
                    if (languages.size > 5) {
                        Text(
                            text = "+${languages.size - 5}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun PreviewObjectCharacterRecognitionSettings() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    ObjectCharacterRecognitionSettings(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel,
        ),
        navigationViewModel = navigationViewModel,
        objectCharacterRecognitionViewModel = ObjectCharacterRecognitionViewModel(
            context = LocalContext.current,
            objectCharacterRecognitionRepository = ObjectCharacterRecognitionRepositoryMemoryRepository(),
            externalObjectCharacterRecognitionModelsRepository = ExternalObjectCharacterRecognitionModelsMemoryRepository()
        ),
    )
}

