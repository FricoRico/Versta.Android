package app.versta.translate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.databaseModule
import app.versta.translate.translateModule
import app.versta.translate.ui.component.ButtonCard
import app.versta.translate.ui.component.ButtonCardDefaults
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.TranslationTextField
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing
import org.koin.compose.KoinApplication

@Composable
fun Home(navController: NavHostController) {
    val scrollBehavior = rememberScrollState()

    return ScaffoldLargeHeader(
        title = {
            Text(
                text = "Welcome back",
            )
        },
        actions = {
            IconButton(onClick = {
                navController.navigate(Screens.Settings())
            }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
            }
        },
        content = { _, scrollConnection ->
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                modifier = Modifier
                    .verticalScroll(scrollBehavior)
                    .nestedScroll(scrollConnection)
                    .padding(top = MaterialTheme.spacing.medium)
                    .padding(horizontal = MaterialTheme.spacing.medium)
            ) {
                LanguageSelector()

//                        SingleChoiceSegmentedButtonRow(
//                            modifier = Modifier.fillMaxWidth()
//                        ) {
//                            SegmentedButton(
//                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
//                                onClick = {
//                                    navController.navigate(Screens.Camera())
//                                },
//                                selected = false
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Outlined.CameraAlt,
//                                    contentDescription = "Vision"
//                                )
//                            }
//                            SegmentedButton(
//                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
//                                onClick = {
//                                    navController.navigate(Screens.TextTranslation())
//                                },
//                                selected = false
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Outlined.Translate,
//                                    contentDescription = "Text"
//                                )
//                            }
//                            SegmentedButton(
//                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
//                                onClick = { },
//                                enabled = false,
//                                selected = false
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Outlined.MicNone,
//                                    contentDescription = "Voice"
//                                )
//                            }
//                        }

                TranslationTextField(
                    onSubmit = {
                        navController.navigate(Screens.TextTranslation())
                    }
                )

                TrialLicenseCard()

                ButtonCard(
                    onClick = {
                        navController.navigate(Screens.Camera())
                    },
                    title = "Vision",
                    subtitle = "Use your camera to translate",
                    icon = Icons.Outlined.CameraAlt,
                    colors = ButtonCardDefaults.colorsPrimary(),
                )

                ButtonCard(
                    onClick = { /*TODO*/ },
                    title = "Voice",
                    subtitle = "Translate a conversation",
                    icon = Icons.Outlined.MicNone,
                    colors = ButtonCardDefaults.colorsSecondary(),
                )
            }
        }
    )
}

@Preview
@Composable
private fun HomePreview() {
    return KoinApplication(
        application = {
            modules(
                translateModule,
                databaseModule,
            )
        }
    ) {
        Home(navController = rememberNavController())
    }
}
