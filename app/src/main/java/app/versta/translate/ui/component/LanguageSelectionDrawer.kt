package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionDrawer(languageViewModel: LanguageViewModel = koinActivityViewModel()) {
    val languageSelection = languageViewModel.languageSelectionState.collectAsStateWithLifecycle()

    val drawerOpenedState = languageSelection.value != null
    val drawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableLanguages = languageViewModel.availableLanguages.collectAsStateWithLifecycle(emptyList())

    if (drawerOpenedState) {
        ModalBottomSheet(
            sheetState = drawerState,
            onDismissRequest = { languageViewModel.setLanguageSelectionState(null) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.medium)
                    .padding(bottom = MaterialTheme.spacing.large)
            ) {
                if (availableLanguages.value.isEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Column {
                            Text(
                                text = "No languages available",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "You can import a new language through the settings",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
                            )
                        }
                    }
                    return@Box
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                LazyColumn(modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium)) {
                    items(availableLanguages.value.size) {
                        val language = availableLanguages.value[it]

                        ListItem(
                            headlineContent = { Text(language.source.name) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = "Localized description"
                                )
                            },
                            colors =
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                        )
                    }
                }
            }
        }
    }
}
