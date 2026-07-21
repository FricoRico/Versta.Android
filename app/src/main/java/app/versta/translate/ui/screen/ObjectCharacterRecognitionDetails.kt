package app.versta.translate.ui.screen

import android.icu.text.DecimalFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ObjectCharacterRecognitionViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.LanguageBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ModelDeletionConfirmationDialog
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing

@Composable
fun ObjectCharacterRecognitionDetails(
    id: String,
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    objectCharacterRecognitionViewModel: ObjectCharacterRecognitionViewModel
) {
    val model by objectCharacterRecognitionViewModel.getOcrModelDefinition(id)
        .collectAsStateWithLifecycle(null)
    val importedModels by objectCharacterRecognitionViewModel.ocrModelsByState.collectAsStateWithLifecycle(
        app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels()
    )

    if (model == null) {
        return
    }

    val languages = model!!.languages.map { Language.fromIsoCode(it) }
        .distinctBy { it.isoCode }
        .sortedBy { it.name }

    var ocrToBeDeleted by remember { mutableStateOf<String?>(null) }

    val layoutDirection = LocalLayoutDirection.current

    val isInstalled = importedModels.installed.any { it.definition.id == model!!.id }
    val extracted = importedModels.installed.find { it.definition.id == model!!.id }?.extracted

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = model!!.name)
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            if (isInstalled) {
                IconButton(onClick = {
                    ocrToBeDeleted = model!!.id
                }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.round_delete_forever_24),
                        contentDescription = stringResource(R.string.delete)
                    )
                }
            }
        },
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
            Details(
                definition = model!!,
                extracted = extracted
            )

            ListDivider()

            Languages(
                languages = languages
            )
        }

        ModelDeletionConfirmationDialog(
            model = ocrToBeDeleted,
            titleRes = R.string.delete_ocr_model_title,
            descriptionRes = R.string.delete_ocr_model_description,
            onConfirmation = { id ->
                objectCharacterRecognitionViewModel.deleteOcrModel(id)
                navigationViewModel.navigate(Screens.ObjectCharacterRecognitionSettings, Screens.ObjectCharacterRecognitionDetails(id))
                ocrToBeDeleted = null
            },
            onDismissRequest = {
                ocrToBeDeleted = null
            }
        )
    }
}

fun LazyListScope.Details(
    definition: ExternalObjectCharacterRecognitionModelDefinition,
    extracted: Long?
) {
    val sizeFormat = DecimalFormat("#.##")
    val size = definition.size / 1e6
    val extractedSize = extracted?.div(1e6)

    return item {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .padding(
                        vertical = MaterialTheme.spacing.medium,
                        horizontal = MaterialTheme.spacing.large,
                    )
                    .fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OcrDetailsData(
                        label = stringResource(R.string.language_details_download_size_label),
                        value = "${sizeFormat.format(size)} MB",
                    )

                    if (extractedSize != null) {
                        OcrDetailsData(
                            label = stringResource(R.string.language_details_disk_size_label),
                            value = "${sizeFormat.format(extractedSize)} MB",
                        )
                    }
                }

                OcrDetailsData(
                    label = stringResource(R.string.language_details_base_model_label),
                    value = definition.baseModel,
                )

                OcrDetailsData(
                    label = stringResource(R.string.language_details_architecture_label),
                    value = definition.architectures.joinToString(", ") { architecture -> architecture.name },
                )

                OcrDetailsData(
                    label = stringResource(R.string.language_details_version_label),
                    value = definition.version,
                )
            }
        }
    }
}

fun LazyListScope.Languages(
    languages: List<Language>
) {
    return item {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = MaterialTheme.spacing.extraSmall)
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                modifier = Modifier
                    .padding(
                        vertical = MaterialTheme.spacing.medium,
                        horizontal = MaterialTheme.spacing.large,
                    )
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ocr_details_supported_languages_label),
                    style = MaterialTheme.typography.labelLarge
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    languages.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { language ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    LanguageBadge(
                                        language = language,
                                        size = MaterialTheme.spacing.medium,
                                    )
                                    Text(
                                        text = language.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OcrDetailsData(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
@Preview(showBackground = true)
fun ObjectCharacterRecognitionDetailsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    ObjectCharacterRecognitionDetails(
        id = "paddle-ocr",
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        objectCharacterRecognitionViewModel = ObjectCharacterRecognitionViewModel(
            context = LocalContext.current,
            objectCharacterRecognitionRepository = ObjectCharacterRecognitionRepositoryMemoryRepository(),
            externalObjectCharacterRecognitionModelsRepository = ExternalObjectCharacterRecognitionModelsMemoryRepository()
        )
    )
}

