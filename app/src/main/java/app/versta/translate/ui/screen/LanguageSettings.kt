package app.versta.translate.ui.screen

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.component.SettingsListItem
import app.versta.translate.ui.component.SwipeDelete
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.TarExtractor
import app.versta.translate.utils.darken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettings(
    navController: NavController,
    languageViewModel: LanguageViewModel,
) {
    val sourceLanguages by languageViewModel.sourceLanguages.collectAsStateWithLifecycle(emptyList())
    val availableLanguages by languageViewModel.availableLanguages.collectAsStateWithLifecycle(
        emptyList()
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val collapsedFraction = scrollBehavior.state.collapsedFraction

    val expandedFontSize = MaterialTheme.typography.displaySmall.fontSize.value
    val collapsedFontSize = MaterialTheme.typography.titleLarge.fontSize.value

    val expandedLineHeight = MaterialTheme.typography.displaySmall.lineHeight.value
    val collapsedLineHeight = MaterialTheme.typography.titleLarge.lineHeight.value

    val context = LocalContext.current

    val titleFontSize by animateFloatAsState(
        targetValue = (collapsedFontSize + (expandedFontSize - collapsedFontSize) * (1 - collapsedFraction)),
        label = "title-size"
    )

    val lineHeight by animateFloatAsState(
        targetValue = (collapsedLineHeight + (expandedLineHeight - collapsedLineHeight) * (1 - collapsedFraction)),
        label = "line-height"
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Languages",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = titleFontSize.sp,
                        lineHeight = lineHeight.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    titleContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
                modifier = Modifier.clip(
                    RoundedCornerShape(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp),
                        bottomStart = MaterialTheme.shapes.extraLarge.bottomStart,
                        bottomEnd = MaterialTheme.shapes.extraLarge.bottomEnd,
                    )
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(
                        top = innerPadding.calculateTopPadding() - MaterialTheme.spacing.extraLarge,
                        bottom = innerPadding.calculateBottomPadding()
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MaterialTheme.spacing.extraSmall)
                        .padding(top = MaterialTheme.spacing.extraLarge)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.small)
                            .clip(MaterialTheme.shapes.extraLarge)
                    ) {
                        SettingsListItem(
                            headlineContent = "Get more languages",
                            supportingContent = "Download or import new language",
                            onClick = {
                                navController.navigate(Screens.LanguageImport())
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                            MaterialTheme.shapes.extraLarge
                                        )
                                        .padding(MaterialTheme.spacing.small),
                                ) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "License",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                headlineColor = MaterialTheme.colorScheme.onPrimary,
                                supportingColor = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
                            ),
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                    ) {
                        items(sourceLanguages, key = { it.locale.language }) { language ->
                            val flagDrawable = language.getFlagDrawable(context)
                            val availableTargetLanguages =
                                availableLanguages.count { it.source == language }

                            SwipeDelete(
                                item = language,
                                onDelete = { /*TODO*/ },
                            ) {
                                SettingsListItem(
                                    headlineContent = language.name,
                                    supportingContent = "$availableTargetLanguages target languages available",
                                    leadingContent = {
                                        Image(
                                            painter = painterResource(flagDrawable),
                                            contentDescription = "Flag",
                                            modifier = Modifier
                                                .requiredSize(MaterialTheme.spacing.extraLarge)
                                                .clip(MaterialTheme.shapes.extraLarge)
                                        )
                                    },
                                    onClick = {},
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
private fun PreviewLanguageSettings() {
    LanguageSettings(
        navController = rememberNavController(),
        languageViewModel = LanguageViewModel(
            modelExtractor = TarExtractor(LocalContext.current),
            languageDatabaseRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
    )
}