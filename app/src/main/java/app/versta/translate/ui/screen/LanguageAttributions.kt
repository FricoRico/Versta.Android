package app.versta.translate.ui.screen

import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.theme.spacing
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageAttributions(
    navController: NavController,
    languageViewModel: LanguageViewModel
) {
    val context = LocalContext.current
    val orientation = context.resources.configuration.orientation

    val languageModels by languageViewModel.languageModels.collectAsStateWithLifecycle(emptyList())

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        0.dp
    }

    fun onBackNavigation() {
        navController.popBackStack()
    }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = stringResource(R.string.language_models_title)
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                ),
            ) {
                items(items = languageModels, key = { it.pair.uniqueId() }) { model ->
                    for(metadata in model.metadata) {
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
    )
}

@Composable
@Preview(showBackground = true)
fun LanguageAttributionsPreview() {
    LanguageAttributions(
        navController = NavController(LocalContext.current),
        languageViewModel = LanguageViewModel(
            context = LocalContext.current,
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
        )
    )
}