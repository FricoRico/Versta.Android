package app.versta.translate.ui.screen

import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.icu.text.DecimalFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalLanguageDownloadTask
import app.versta.translate.core.entity.ExternalLanguageModels
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.LANGUAGE_RATING_THRESHOLD
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.DownloadButton
import app.versta.translate.ui.component.LanguageDeletionConfirmationDialog
import app.versta.translate.ui.component.LanguagePairBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.theme.spacing
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettings(
    navController: NavController,
    languageViewModel: LanguageViewModel,
) {
    val context = LocalContext.current

    val orientation = context.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    val languageModels by languageViewModel.languageModelsByState.collectAsStateWithLifecycle(
        ExternalLanguageModels()
    )
    val downloadTasks by languageViewModel.downloadTasks.collectAsStateWithLifecycle()

    var languageToBeDeleted by remember { mutableStateOf<LanguagePair?>(null) }

    val queuedTasks = (languageModels.updates + languageModels.available).filter { model ->
        downloadTasks.any { it.model == model }
    }
    val filteredUpdates = languageModels.updates.filterNot { model ->
        downloadTasks.any { it.model == model }
    }
    val filteredAvailable = languageModels.available.filterNot { model ->
        downloadTasks.any { it.model == model }
    }

    fun onDownload(model: ExternalLanguagePairDefinition) {
        languageViewModel.queueDownload(context, model)
    }

    fun onCancel() {
        languageViewModel.cancelDownload(context)
    }

    fun onClick(pair: LanguagePair) {
        navController.navigate(Screens.LanguageDetails.withArgs(pair.id))
    }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = stringResource(R.string.language_settings_title),
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                )
            ) {
                Languages(
                    languages = queuedTasks,
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
                        languageToBeDeleted = language
                    }
                )

                Languages(
                    languages = languageModels.installed,
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
                    onSwipeToDelete = { language ->
                        languageToBeDeleted = language
                    }
                )

                Languages(
                    languages = filteredUpdates,
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
                    onSwipeToDelete = { language ->
                        languageToBeDeleted = language
                    }
                )

                Languages(
                    languages = filteredAvailable,
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

            LanguageDeletionConfirmationDialog(
                pair = languageToBeDeleted,
                onConfirmation = {
                    languageViewModel.removeLanguageModel(it, true)
                    languageToBeDeleted = null
                },
                onDismissRequest = {
                    languageToBeDeleted = null
                })
        })
}

private fun LazyListScope.Languages(
    languages: List<ExternalLanguagePairDefinition>,
    header: @Composable (Int) -> Unit,
    downloadTasks: List<ExternalLanguageDownloadTask>,
    onClick: (LanguagePair) -> Unit,
    onDownload: ((ExternalLanguagePairDefinition) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSwipeToDelete: ((LanguagePair) -> Unit)? = null,
) {
    if (languages.isEmpty()) {
        return
    }

    val ratingFormat = DecimalFormat("0.0")
    val sizeFormat = DecimalFormat("0.00")

    item {
        header(languages.size)
    }

    items(
        count = languages.size,
        key = { languages[it].pair.uniqueId() }
    ) { it ->
        val model = remember { languages[it] }
        val task = downloadTasks.firstOrNull { it.model == model }

        val source = model.pair.source
        val target = model.pair.target

        val score = model.metadata.map { it.score }.average()
        val rating = max(min((score / LANGUAGE_RATING_THRESHOLD) * 5, 5.0), 1.0)

        val size = ((model.extracted ?: model.size) / 1e6)

        SettingsButtonItem(
            index = it + 1,
            groupSize = languages.size + 1,
            modifier = Modifier.animateItem(),
            colors = SettingsDefaults.colors(supportingColor = MaterialTheme.colorScheme.onSurfaceVariant),
            headlineContent = "${source.name} - ${target.name}",
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
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredSize(MaterialTheme.spacing.medium)
                        )
                        Text(
                            text = ratingFormat.format(rating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (model.extracted != null) Icons.Filled.Save else Icons.Filled.CloudDownload,
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
            leadingContent = {
                LanguagePairBadge(
                    pair = model.pair,
                    bidirectional = model.bidirectional,
                )
            },
            onClick = { onClick(model.pair) },
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
                { onSwipeToDelete(model.pair) }
            } else {
                null
            })
    }

    ListDivider()
}

@Composable
@Preview(showBackground = true)
@SuppressLint("ViewModelConstructorInComposable")
private fun PreviewLanguageSettings() {
    LanguageSettings(
        navController = rememberNavController(),
        languageViewModel = LanguageViewModel(
            context = LocalContext.current,
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
        ),
    )
}