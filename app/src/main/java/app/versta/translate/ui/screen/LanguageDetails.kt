package app.versta.translate.ui.screen

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.LanguageDeletionConfirmationDialog
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDetails(
    navController: NavController,
    languageViewModel: LanguageViewModel,
) {
    val argument = navController.currentBackStackEntry?.arguments?.getString("sourceLanguage")
    if (argument == null) {
        navController.popBackStack()
        // TODO: Add log entry
        return
    }

    val language = Language.fromIsoCode(argument)

    val availableLanguagePairs by languageViewModel.availableLanguagePairs.collectAsStateWithLifecycle(emptyList())
    val availableLanguages by languageViewModel.availableLanguages.collectAsStateWithLifecycle(emptyList())

    val targetLanguagePairs = availableLanguagePairs.filter { it.target == language }
    val targetLanguages = availableLanguages.filter { it.pair in targetLanguagePairs }

    val context = LocalContext.current

    val orientation = context.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    var languageToBeDeleted by remember { mutableStateOf<Language?>(null) }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = language.name,
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
                Languages(context = context,
                    targetLanguages = targetLanguages,
                    onSwipeToDelete = { language ->
                        languageToBeDeleted = language
                    })
            }
        }
    )

    LanguageDeletionConfirmationDialog(
        language = languageToBeDeleted,
        availableLanguages = availableLanguagePairs,
        onConfirmation = {
            languageViewModel.deleteBySource(it)
            languageToBeDeleted = null
        },
        onDismissRequest = {
            languageToBeDeleted = null
        })
}

private fun LazyListScope.Languages(
    context: Context,
    targetLanguages: List<LanguagePairWithModelFiles>,
    onSwipeToDelete: (Language) -> Unit,
) {
    if (targetLanguages.isEmpty()) {
        return
    }

    items(
        targetLanguages.size,
        key = { index -> targetLanguages[index].pair.source.locale.language }) { index ->
        val language = remember { targetLanguages[index] }

        val flagDrawable = remember { language.pair.source.getFlagDrawable(context) }

        SettingsButtonItem(index = index,
            groupSize = targetLanguages.size,
            headlineContent = language.pair.source.name,
            leadingContent = {
                Image(
                    painter = painterResource(flagDrawable),
                    contentDescription = stringResource(R.string.flag, language.pair.source.name),
                    modifier = Modifier
                        .requiredSize(MaterialTheme.spacing.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
            },
            supportingContent = stringResource(
                R.string.version,
                language.files.version.replace("v", "")
            ),
            trailingContent = {
                IconButton(
                    onClick = { onSwipeToDelete(language.pair.source) },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            },
            onSwipeToDelete = { onSwipeToDelete(language.pair.source) })
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageDetailsPreview() {
    LanguageDetails(
        navController = rememberNavController(),
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        )
    )
}