package app.versta.translate.ui.screen

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.theme.spacing
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults

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

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = "Languages",
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
                        headlineContent = "Get more languages",
                        supportingContent = "Download or import new language",
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
                                    contentDescription = "License",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        },
                        colors = SettingsDefaults.colorsSecondary(),
                    )
                }

                ListDivider()

                Languages(context = context,
                    sourceLanguages = sourceLanguages,
                    availableLanguages = availableLanguages,
                    onClick = { language ->
//                        navController.navigate(Screens.LanguageSelection(language.locale.language))
                    },
                    onSwipeToDelete = { language ->
                        languageToBeDeleted = language
                    })
            }

            LanguageDeletionConfirmationDialog(context = context,
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

@Composable
private fun LanguageDeletionConfirmationDialog(
    context: Context,
    language: Language?,
    availableLanguages: List<LanguagePair>,
    onConfirmation: (Language) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (language == null) {
        return
    }

    val flagDrawable = remember { language.getFlagDrawable(context) }
    val targetLanguages = availableLanguages.filter { it.source == language }.map { it.target }

    AlertDialog(onDismissRequest = {
        onDismissRequest()
    }, icon = {
        Icon(
            Icons.Outlined.Error,
            contentDescription = "Warning",
            tint = MaterialTheme.colorScheme.error
        )
    }, title = {
        Text(text = stringResource(R.string.delete_language_title, language.name))
    }, text = {
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
            item {
                Text(
                    text = "Are you sure you want to remove ${language.name}? This will delete the following translation options:",
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium)
                )
            }

            items(targetLanguages, key = { it.locale }) { targetLanguage ->
                val targetFlagDrawable = remember { targetLanguage.getFlagDrawable(context) }
                val isBirectional =
                    remember { availableLanguages.any { it.source == targetLanguage && it.target == language } }

                Box(
                    modifier = Modifier.padding(
                        vertical = MaterialTheme.spacing.extraSmall,
                        horizontal = MaterialTheme.spacing.small
                    )
                ) {
                    Icon(
                        if (isBirectional) Icons.Outlined.SyncAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp),
                        contentDescription = null,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                space = MaterialTheme.spacing.small,
                            )
                        ) {
                            Image(
                                painter = painterResource(flagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, language.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.medium)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )

                            Text(text = language.name)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                space = MaterialTheme.spacing.small,
                            )
                        ) {
                            Text(text = targetLanguage.name)

                            Image(
                                painter = painterResource(targetFlagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, targetLanguage.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.medium)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }

                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            onConfirmation(language)
        }) {
            Text("Confirm")
        }
    }, dismissButton = {
        TextButton(onClick = {
            onDismissRequest()
        }) {
            Text("Dismiss")
        }
    })
}

private fun LazyListScope.Languages(
    context: Context,
    sourceLanguages: List<Language>,
    availableLanguages: List<LanguagePair>,
    onClick: (Language) -> Unit,
    onSwipeToDelete: (Language) -> Unit,
) {
    items(sourceLanguages.size,
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