package app.versta.translate.ui.screen

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing

@Composable
fun LanguageAttributions(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    languageViewModel: LanguageViewModel
) {
    val context = LocalContext.current

    val layoutDirection = LocalLayoutDirection.current

    val languageModels by languageViewModel.languageModels.collectAsStateWithLifecycle(emptyList())

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.language_models_title))
        },
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
            items(items = languageModels, key = { it.pair.uniqueId() }) { model ->
                for (metadata in model.metadata) {
                    val onClick = Intent(
                        Intent.ACTION_VIEW,
                        stringResource(R.string.hugginface_url, metadata.baseModel).toUri()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = {
                            context.startActivity(onClick, null)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.medium),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.language_attributes_source_target_combination,
                                        metadata.source.name,
                                        metadata.target.name
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1
                                )

                                Text(
                                    text = metadata.baseModel,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                Row(
                                    modifier = Modifier.padding(top = MaterialTheme.spacing.small)
                                ) {
                                    metadata.architectures.forEach { architecture ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
                                        ) {
                                            Text(
                                                text = architecture.name,
                                                modifier = Modifier.padding(
                                                    vertical = 1.dp,
                                                    horizontal = MaterialTheme.spacing.extraSmall
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = model.version.replace("v", ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageAttributionsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    LanguageAttributions(
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