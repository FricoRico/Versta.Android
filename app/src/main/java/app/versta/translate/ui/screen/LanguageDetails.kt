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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.ExternalLanguageMetadata
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.LANGUAGE_RATING_THRESHOLD
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.LanguageDeletionConfirmationDialog
import app.versta.translate.ui.component.LanguagePairBadge
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

internal const val TAG = "LanguageDetails"

@Composable
fun LanguageDetails(
    id: String,
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    languageViewModel: LanguageViewModel,
) {
    Timber.tag("LanguageDetails").d("Showing details for language pair: $id")
    val pair = LanguagePair.fromId(id)
    val model by languageViewModel.getLanguageDefinition(pair).collectAsStateWithLifecycle(null)
    val importedLanguagePairs by languageViewModel.importedLanguagePairs.collectAsStateWithLifecycle(
        emptyList()
    )

    var languageToBeDeleted by remember { mutableStateOf<LanguagePair?>(null) }

    val layoutDirection = LocalLayoutDirection.current

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = "${pair.source.name} - ${pair.target.name}")
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            if (importedLanguagePairs.contains(
                    pair
                )
            ) {
                IconButton(onClick = {
                    languageToBeDeleted = pair
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
            if (model == null) {
                return@LazyColumn
            }

            Details(
                definition = model!!
            )

            ListDivider()

            Metadata(
                metadata = model!!.metadata
            )
        }

        LanguageDeletionConfirmationDialog(pair = languageToBeDeleted, onConfirmation = {
            navigationViewModel.back()

            languageViewModel.removeLanguageModel(it, true)
            languageToBeDeleted = null
        }, onDismissRequest = {
            languageToBeDeleted = null
        })
    }
}

fun LazyListScope.Details(
    definition: ExternalLanguagePairDefinition
) {
    val sizeFormat = DecimalFormat("#.##")
    val size = definition.size / 1e6
    val extracted = definition.extracted?.div(1e6)

    return item {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
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
                    LanguageDetailsData(
                        label = stringResource(R.string.language_details_download_size_label),
                        value = "${sizeFormat.format(size)} MB",
                    )

                    if (extracted != null) {
                        LanguageDetailsData(
                            label = stringResource(R.string.language_details_disk_size_label),
                            value = "${sizeFormat.format(extracted)} MB",
                        )
                    }
                }

                LanguageDetailsData(
                    label = stringResource(R.string.language_details_bidirectional_label),
                    value = if (definition.bidirectional) {
                        stringResource(R.string.yes)
                    } else {
                        stringResource(R.string.no)
                    },
                )

                LanguageDetailsData(
                    label = stringResource(R.string.language_details_version_label),
                    value = definition.version,
                )
            }
        }
    }
}

fun LazyListScope.Metadata(
    metadata: List<ExternalLanguageMetadata>
) {
    return items(
        count = metadata.size,
        key = { "${metadata[it].source}-${metadata[it].target}" },
    ) {
        val data = metadata[it]
        val first = it == 0
        val last = it == metadata.size - 1

        val pair = LanguagePair(
            source = data.source, target = data.target
        )

        val scoreFormat = DecimalFormat("#.#")
        val ratingFormat = DecimalFormat("#.#")
        val rating = max(min((data.score / LANGUAGE_RATING_THRESHOLD) * 5, 5.0), 1.0)

        val topRounding = if (first) {
            MaterialTheme.spacing.extraLarge
        } else {
            MaterialTheme.spacing.medium
        }

        val bottomRounding = if (last) {
            MaterialTheme.spacing.extraLarge
        } else {
            MaterialTheme.spacing.medium
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(bottom = MaterialTheme.spacing.extraSmall)
                .clip(
                    RoundedCornerShape(
                        topStart = topRounding,
                        topEnd = topRounding,
                        bottomStart = bottomRounding,
                        bottomEnd = bottomRounding,
                    )
                )
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
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.small)
                ) {
                    LanguagePairBadge(
                        pair = pair, bidirectional = false
                    )

                    Text(
                        text = "${data.source.name} - ${data.target.name}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LanguageDetailsData(
                        label = stringResource(R.string.language_details_rating_label),
                        value = ratingFormat.format(rating),
                        icon = ImageVector.vectorResource(R.drawable.round_star_rate_24)
                    )

                    LanguageDetailsData(
                        label = stringResource(R.string.language_details_blue_score_label),
                        value = scoreFormat.format(data.score),
                    )
                }

                LanguageDetailsData(
                    label = stringResource(R.string.language_details_base_model_label),
                    value = data.baseModel,
                )

                LanguageDetailsData(
                    label = stringResource(R.string.language_details_architecture_label),
                    value = data.architectures.joinToString(", ") { architecture -> architecture.name },
                )
            }
        }
    }
}

@Composable
fun LanguageDetailsData(
    label: String,
    value: String,
    icon: ImageVector? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.hairline)
    ) {
        Text(
            text = label, style = MaterialTheme.typography.labelLarge
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
fun LanguageDetailsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    LanguageDetails(
        id = "en-nl",
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        languageViewModel = LanguageViewModel(
            context = LocalContext.current,
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
        )
    )
}