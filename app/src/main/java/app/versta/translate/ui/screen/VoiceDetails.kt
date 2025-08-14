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
import androidx.compose.foundation.layout.requiredSize
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
import app.versta.translate.adapter.outbound.ExternalVoiceModelsMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.core.entity.ExternalVoiceLanguageVoiceGenders
import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.ui.component.LanguageBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.VoiceDeletionConfirmationDialog
import app.versta.translate.ui.theme.spacing

@Composable
fun VoiceDetails(
    id: String,
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    voiceViewModel: VoiceViewModel
) {
    val model by voiceViewModel.getVoiceModelDefinition(id)
        .collectAsStateWithLifecycle(null)
    val importedLanguagePairs by voiceViewModel.importedVoices.collectAsStateWithLifecycle(
        emptyList()
    )

    if (model == null) {
        return
    }

    val voiceLanguages = model?.voices
        ?.groupBy { it.language }
        ?.map { (language, voices) ->
            ExternalVoiceLanguageVoiceGenders(
                language = Language.fromIsoCode(language),
                genders = voices.map { it.gender }
            )
        } ?: emptyList()
    val voiceOptions = mapOf(
        VoiceGender.Female to stringResource(R.string.text_to_speech_settings_voice_gender_female),
        VoiceGender.Male to stringResource(R.string.text_to_speech_settings_voice_gender_male)
    )

    var voiceToBeDeleted by remember { mutableStateOf<ExternalVoiceModelDefinition?>(null) }

    val layoutDirection = LocalLayoutDirection.current

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
            if (importedLanguagePairs.any {
                    it.id == model!!.id
                }) {
                IconButton(onClick = {
                    voiceToBeDeleted = model
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
                definition = model!!
            )

            ListDivider()

            Voices(
                voiceLanguages = voiceLanguages,
                voiceOptions = voiceOptions
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
            }
        )
    }
}

fun LazyListScope.Details(
    definition: ExternalVoiceModelDefinition
) {
    val sizeFormat = DecimalFormat("#.##")
    val size = definition.size / 1e6
    val extracted = definition.extracted?.div(1e6)

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
                    VoiceDetailsData(
                        label = stringResource(R.string.language_details_download_size_label),
                        value = "${sizeFormat.format(size)} MB",
                    )

                    if (extracted != null) {
                        VoiceDetailsData(
                            label = stringResource(R.string.language_details_disk_size_label),
                            value = "${sizeFormat.format(extracted)} MB",
                        )
                    }
                }

                VoiceDetailsData(
                    label = stringResource(R.string.language_details_base_model_label),
                    value = definition.baseModel,
                )

                VoiceDetailsData(
                    label = stringResource(R.string.language_details_architecture_label),
                    value = definition.architectures.joinToString(", ") { architecture -> architecture.name },
                )

                VoiceDetailsData(
                    label = stringResource(R.string.language_details_version_label),
                    value = definition.version,
                )
            }
        }
    }
}

fun LazyListScope.Voices(
    voiceLanguages: List<ExternalVoiceLanguageVoiceGenders>,
    voiceOptions: Map<VoiceGender, String>
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
                voiceLanguages.map {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        LanguageBadge(
                            language = it.language
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline)
                        ) {
                            Text(
                                text = it.language.name,
                                style = MaterialTheme.typography.labelLarge
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                it.genders.map {
                                    Text(
                                        text = voiceOptions[it]
                                            ?: stringResource(R.string.unknown),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun VoiceDetailsData(
    label: String,
    value: String,
    icon: ImageVector? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        if (icon != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.requiredSize(MaterialTheme.spacing.medium)
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun VoiceDetailsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    VoiceDetails(
        id = "kokoro",
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        voiceViewModel = VoiceViewModel(
            context = LocalContext.current,
            voiceRepository = VoiceMemoryRepository(),
            externalVoiceModelsRepository = ExternalVoiceModelsMemoryRepository()
        )
    )
}