package app.versta.translate.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.SettingsListItem
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navController: NavController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val collapsedFraction = scrollBehavior.state.collapsedFraction

    val expandedFontSize = MaterialTheme.typography.displaySmall.fontSize.value
    val collapsedFontSize = MaterialTheme.typography.titleLarge.fontSize.value

    val expandedLineHeight = MaterialTheme.typography.displaySmall.lineHeight.value
    val collapsedLineHeight = MaterialTheme.typography.titleLarge.lineHeight.value

    val titleFontSize by animateFloatAsState(
        targetValue = (collapsedFontSize + (expandedFontSize - collapsedFontSize) * (1 - collapsedFraction)),
        label = "title-size"
    )

    val lineHeight by animateFloatAsState(
        targetValue = (collapsedLineHeight + (expandedLineHeight - collapsedLineHeight) * (1 - collapsedFraction)),
        label = "line-height"
    )

    return Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
                            headlineContent = "Languages",
                            supportingContent = "Import languages, download languages",
                            colors = SettingsDefaults.colorsInverted(),
                            onClick = {
                                navController.navigate(Screens.LanguageSettings())
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Translate,
                                    contentDescription = "Languages",
                                )
                            },
                        )
                        SettingsListItem(
                            headlineContent = "Translation",
                            supportingContent = "Manage history, translator fine-tuning",
                            colors = SettingsDefaults.colorsInverted(),
                            onClick = {/*TODO*/ },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = "Translation",
                                )
                            },
                        )
                    }

                    TrialLicenseCard()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                    ) {
                        SettingsListItem(
                            headlineContent = "Account",
                            supportingContent = "Sign in, sign out, manage account settings",
                            onClick = {/*TODO*/ },
                        )
                        SettingsListItem(
                            headlineContent = "Feedback",
                            supportingContent = "Send feedback, report a problem",
                            onClick = {/*TODO*/ },
                        )
                    }
                }
            }
        }
    )
}

@Composable
@Preview
fun SettingsPreview() {
    return Settings(navController = rememberNavController())
}