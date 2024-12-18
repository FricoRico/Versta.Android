package app.versta.translate.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.ShimmerEffect
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel
import app.versta.translate.utils.lighten
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslation(
    navController: NavController,
    textTranslationViewModel: TextTranslationViewModel = koinActivityViewModel(),
    translationViewModel: TranslationViewModel = koinActivityViewModel(),
) {
    val input by textTranslationViewModel.sourceText.collectAsStateWithLifecycle("")
    val target by textTranslationViewModel.targetText.collectAsStateWithLifecycle("")

    var isTranslating by remember { mutableStateOf(false) }

    val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun translate() {
        isTranslating = true
        val startTime = System.currentTimeMillis()
        processingScope.launch {
            val output = translationViewModel.translate(input)
            val elapsedTime = System.currentTimeMillis() - startTime

            textTranslationViewModel.setTargetText(output)

            Log.d("TranslationTextFieldMinimal", "Translated in ${elapsedTime}ms: $output")

            isTranslating = false
        }
    }

    if (input.isNotEmpty()) {
        LaunchedEffect(Unit) {
            translate()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    Text(
                        text = "Translator"
                    )
                },
            )
        },
        content = { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(
                        bottom = MaterialTheme.spacing.large
                    )
            ) {
//                LanguageSelector(
//                    modifier = Modifier
//                        .padding(
//                            horizontal = MaterialTheme.spacing.extraSmall
//                        )
//                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacing.extraSmall
                        )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            TextField(
                                placeholder = "Type something",
                                modifier = Modifier
                                    .padding(top = MaterialTheme.spacing.small)
                                    .padding(horizontal = MaterialTheme.spacing.small)
                                    .defaultMinSize(minHeight = 192.dp),
                                value = input,
                                onValueChange = {
                                    textTranslationViewModel.setSourceText(it)
                                },
                                onSubmit = {
                                    translate()
                                },
                                maxLines = 12,
                                colors = TextFieldDefaults.colorsTransparentInverse()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (isTranslating) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                                    modifier = Modifier
                                        .padding(MaterialTheme.spacing.large)
                                ) {
                                    repeat(4) {
                                        ShimmerEffect(
                                            modifier = Modifier
                                                .height(MaterialTheme.spacing.medium)
                                                .fillMaxWidth(if (it == 3) 0.33f else 1f)
                                                .background(
                                                    color = MaterialTheme.colorScheme.onTertiary.lighten(
                                                        0.8f
                                                    ),
                                                )
                                                .clip(MaterialTheme.shapes.extraLarge),
                                            widthOfShadowBrush = 1000,
                                            durationMillis = 1000
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = target,
                                    modifier = Modifier
                                        .padding(MaterialTheme.spacing.large),
                                    color = MaterialTheme.colorScheme.onTertiary
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
@Preview
fun TextTranslationPreview() {
    TextTranslation(
        navController = rememberNavController()
    )
}