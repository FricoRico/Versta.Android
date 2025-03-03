package app.versta.translate.ui.component

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.LanguageType
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDrawer(
    languageViewModel: LanguageViewModel,
    modifier: Modifier = Modifier,
) {
    val languageSelection = languageViewModel.languageSelectionState.collectAsStateWithLifecycle()

    val drawerOpenedState = languageSelection.value != null
    val drawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sourceLanguages by languageViewModel.sourceLanguages.collectAsStateWithLifecycle(emptyList())
    val targetLanguages by languageViewModel.targetLanguages.collectAsStateWithLifecycle(emptyList())

    val context = LocalContext.current

    if (!drawerOpenedState) {
        return
    }

    ModalBottomSheet(
        sheetState = drawerState,
        onDismissRequest = { languageViewModel.setLanguageSelectionState(null) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.small)
                .padding(bottom = MaterialTheme.spacing.large)
                .then(modifier)
        ) {
            when (languageSelection.value) {
                LanguageType.Source -> {
                    LanguageSelectionSourceLanguage(
                        context = context,
                        languages = sourceLanguages,
                        onClick = {
                            languageViewModel.setSourceLanguage(it)
                            languageViewModel.setLanguageSelectionState(null)
                        }
                    )
                }

                LanguageType.Target -> {
                    LanguageSelectionTargetLanguage(
                        context = context,
                        languages = targetLanguages,
                        onClick = {
                            languageViewModel.setTargetLanguage(it)
                            languageViewModel.setLanguageSelectionState(null)
                        }
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
fun LanguageSelectionSourceLanguage(
    context: Context,
    languages: List<Language>,
    onClick: (language: Language) -> Unit
) {
    if (languages.isEmpty()) {
        LanguageSelectionNoItems()
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.select_source_language),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.large)
            )
        }

        LazyColumn(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            items(languages, key = { it.locale.language }) {
                LanguageSelectionListItem(
                    context = context,
                    language = it,
                    onClick = {
                        onClick(it)
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageSelectionTargetLanguage(
    context: Context,
    languages: List<Language>,
    onClick: (language: Language) -> Unit
) {
    if (languages.isEmpty()) {
        LanguageSelectionNoItems()
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.select_target_language),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.large)
            )
        }

        LazyColumn(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            items(languages, key = { it.locale.language }) {
                LanguageSelectionListItem(
                    context = context,
                    language = it,
                    onClick = {
                        onClick(it)
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageSelectionNoItems() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column {
            Text(
                text = stringResource(R.string.no_languages_available),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.language_import_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
            )
        }
    }
}

@Composable
fun LanguageSelectionListItem(
    context: Context,
    language: Language,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagDrawable = language.getFlagDrawable(context)

    ListItem(
        headlineContent = {
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
            )
        },
        leadingContent = {
            Image(
                painter = painterResource(flagDrawable),
                contentDescription = stringResource(
                    R.string.flag, language.name
                ),
                modifier = Modifier
                    .requiredSize(MaterialTheme.spacing.extraLarge)
                    .clip(MaterialTheme.shapes.extraLarge)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 64.dp)
            .clickable(
                onClick = { onClick() },
            )
            .then(modifier),
    )
}

@Composable
@Preview(showBackground = true)
fun LanguageSelectionDrawerPreview() {
    LanguageSelectionDrawer(
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ).apply {
            setLanguageSelectionState(LanguageType.Source)
        },
    )
}
