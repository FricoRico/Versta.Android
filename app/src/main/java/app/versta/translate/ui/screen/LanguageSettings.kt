package app.versta.translate.ui.screen

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.LanguageDeletionConfirmationDialog
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.theme.spacing

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


    val sourceLanguages by languageViewModel.sourceLanguages.collectAsStateWithLifecycle(emptyList())
    val availableLanguages by languageViewModel.availableLanguagePairs.collectAsStateWithLifecycle(
        emptyList()
    )

    var languageToBeDeleted by remember { mutableStateOf<Language?>(null) }

    ScaffoldLargeHeader(topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
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
                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.language_settings_get_more_title),
                        supportingContent = stringResource(R.string.language_settings_get_more_description),
                        onClick = {
                            navController.navigate(Screens.LanguageImport())
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.shapes.extraLarge
                                    )
                                    .padding(MaterialTheme.spacing.small),
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        },
                        colors = SettingsDefaults.colorsSecondary(),
                    )
                }

                ListDivider()

                if (availableLanguages.isEmpty()) {
                    item {
                        Row(
                            Modifier
                                .padding(top = MaterialTheme.spacing.extraLarge)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.language_settings_no_languages),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.language_settings_language_import_hint),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
                                )
                            }
                        }
                    }
                }

                Languages(context = context,
                    sourceLanguages = sourceLanguages,
                    availableLanguages = availableLanguages,
                    onClick = { language ->
                        navController.navigate(Screens.LanguageDetails.withArgs(language.locale.language))
                    },
                    onSwipeToDelete = { language ->
                        languageToBeDeleted = language
                    })
            }

            LanguageDeletionConfirmationDialog(
                language = languageToBeDeleted,
                availableLanguages = availableLanguages,
                onConfirmation = {
                    languageViewModel.deleteBySource(it)
                    languageToBeDeleted = null
                },
                onDismissRequest = {
                    languageToBeDeleted = null
                })
        })
}

private fun LazyListScope.Languages(
    context: Context,
    sourceLanguages: List<Language>,
    availableLanguages: List<LanguagePair>,
    onClick: (Language) -> Unit,
    onSwipeToDelete: (Language) -> Unit,
) {
    if (availableLanguages.isEmpty()) {
        return
    }

    items(
        sourceLanguages.size,
        key = { index -> sourceLanguages[index].locale.language }) { index ->
        val language = remember { sourceLanguages[index] }

        val flagDrawable = remember { language.getFlagDrawable(context) }
        val availableTargetLanguages =
            remember { availableLanguages.count { it.source == language } }

        SettingsButtonItem(index = index,
            groupSize = sourceLanguages.size,
            headlineContent = language.name,
            supportingContent = stringResource(
                R.string.target_languages_available, availableTargetLanguages
            ),
            leadingContent = {
                Image(
                    painter = painterResource(flagDrawable),
                    contentDescription = stringResource(R.string.flag, language.name),
                    modifier = Modifier
                        .requiredSize(MaterialTheme.spacing.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
            },
            onClick = { onClick(language) },
            onSwipeToDelete = { onSwipeToDelete(language) })
    }
}

@Composable
@Preview(showBackground = true)
private fun PreviewLanguageSettings() {
    LanguageSettings(
        navController = rememberNavController(),
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
    )
}