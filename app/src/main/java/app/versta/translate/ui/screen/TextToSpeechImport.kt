package app.versta.translate.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.inbound.FilePickerCallback
import app.versta.translate.adapter.inbound.ModelFilePicker
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.TextToSpeechMemoryRepository
import app.versta.translate.core.entity.TextToSpeechAnalysisProgress
import app.versta.translate.core.entity.TextToSpeechImportProgress
import app.versta.translate.core.model.TextToSpeechImportViewModel
import app.versta.translate.ui.component.ScaffoldBottomPage
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.annotateSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechImport(
    navController: NavController,
    textToSpeechImportViewModel: TextToSpeechImportViewModel
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    fun onBackNavigation() {
        if (pagerState.currentPage == 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
            return
        }

        navController.popBackStack()
    }

    BackHandler {
        onBackNavigation()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    PageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        onBackNavigation()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.back)
                        )
                    }
                },
            )
        },
        content = { innerPadding ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {
                Column {
                    when (pagerState.currentPage) {
                        0 -> TextToSpeechSelectionPage(
                            context,
                            coroutineScope,
                            pagerState,
                            textToSpeechImportViewModel,
                            innerPadding
                        )

                        1 -> TextToSpeechAnalysisPage(
                            context,
                            coroutineScope,
                            pagerState,
                            textToSpeechImportViewModel,
                            innerPadding
                        )

                        2 -> TextToSpeechProgressPage(
                            coroutineScope,
                            pagerState,
                            textToSpeechImportViewModel,
                            innerPadding
                        )

                        3 -> TextToSpeechFinishedPage(
                            textToSpeechImportViewModel,
                            innerPadding
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun TextToSpeechSelectionPage(
    context: Context,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    textToSpeechImportViewModel: TextToSpeechImportViewModel,
    innerPadding: PaddingValues
) {
    val onPickExistingFile: FilePickerCallback = object : FilePickerCallback {
        override fun onFilePicked(uri: Uri) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }

            textToSpeechImportViewModel.analyze(uri)
        }
    }

    val onDownloadNewFile = Intent(
        Intent.ACTION_VIEW,
        stringResource(R.string.speech_models_url, stringResource(R.string.site_url)).toUri()
    )

    ScaffoldBottomPage(
        innerPadding = innerPadding
    ) {
        item {
            Text(
                text = stringResource(R.string.text_to_speech_import_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        item {
            Text(
                text = stringResource(R.string.text_to_speech_import_introduction),
                modifier = Modifier
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                    )
                    .padding(
                        top = MaterialTheme.spacing.extraLarge
                    ),
            )
        }

        item {
            Text(
                text = annotateSentence(
                    sentence = stringResource(R.string.import_file_type_explanation),
                    annotation = stringResource(R.string.import_file_type),
                    style = SpanStyle(fontWeight = FontWeight.Bold)
                ),
                modifier = Modifier
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                    ),
            )
        }

        item {
            Column(
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.large)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                    colors = ButtonDefaults.transparentButtonColors(),
                    onClick = {
                        context.startActivity(onDownloadNewFile, null)
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Text(text = stringResource(R.string.get_file))
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                    onClick = {
                        ModelFilePicker.openFilePicker(onPickExistingFile)
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Text(text = stringResource(R.string.choose_file))
                        Icon(
                            Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextToSpeechAnalysisPage(
    context: Context,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    textToSpeechImportViewModel: TextToSpeechImportViewModel,
    innerPadding: PaddingValues
) {
    val analysisProgress by textToSpeechImportViewModel.analysisProgressState.collectAsState()

    fun onApprove(uri: Uri) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }

        textToSpeechImportViewModel.import(uri, context.filesDir)
    }

    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels

    Box {
        AnimatedVisibility(
            visible = analysisProgress is TextToSpeechAnalysisProgress.Idle || analysisProgress is TextToSpeechAnalysisProgress.InProgress,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(MaterialTheme.spacing.extraLarge)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = analysisProgress is TextToSpeechAnalysisProgress.Error,
            enter = slideInVertically(
                initialOffsetY = { screenHeight },
            ),
            exit = slideOutVertically(
                targetOffsetY = { screenHeight },
            ),
        ) {
            val analysis = analysisProgress as TextToSpeechAnalysisProgress.Error

            ScaffoldBottomPage(
                innerPadding = innerPadding
            ) {
                item {
                    Text(
                        text = stringResource(R.string.analysis_failed_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.analysis_failed_explanation),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(
                                horizontal = MaterialTheme.spacing.medium,
                            )
                            .padding(
                                top = MaterialTheme.spacing.extraLarge
                            ),
                    )
                }

                item {
                    Text(
                        text = analysis.exception.message
                            ?: stringResource(R.string.unknown_error),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .padding(
                                horizontal = MaterialTheme.spacing.medium,
                            )
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = analysisProgress is TextToSpeechAnalysisProgress.Completed,
        enter = slideInVertically(
            initialOffsetY = { screenHeight },
        ),
        exit = slideOutVertically(
            targetOffsetY = { screenHeight },
        ),
    ) {
        val analysis = analysisProgress as TextToSpeechAnalysisProgress.Completed
        val uri = remember { analysis.uri }

        ScaffoldBottomPage(
            innerPadding = innerPadding
        ) {
            item {
                Text(
                    text = stringResource(R.string.text_to_speech_analysis_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.text_to_speech_analysis_explanation),
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                        .padding(
                            top = MaterialTheme.spacing.extraLarge
                        ),
                )
            }

            item {
                Button(
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.large)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                    onClick = {
                        onApprove(uri)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.text_to_speech_analysis_approve),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextToSpeechProgressPage(
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    textToSpeechImportViewModel: TextToSpeechImportViewModel,
    innerPadding: PaddingValues
) {
    val importProgress by textToSpeechImportViewModel.importProgressState.collectAsState()
    fun onFinished() {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels

    when (importProgress) {
        is TextToSpeechImportProgress.Idle, TextToSpeechImportProgress.Started, is TextToSpeechImportProgress.InProgress -> {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(MaterialTheme.spacing.extraLarge)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = importProgress is TextToSpeechImportProgress.InProgress,
                    enter = slideInVertically(
                        initialOffsetY = { screenHeight },
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { screenHeight },
                    ),
                ) {
                    val progress = importProgress as TextToSpeechImportProgress.InProgress

                    ScaffoldBottomPage(
                        innerPadding = innerPadding
                    ) {
                        item {
                            LinearProgressIndicator(
                                progress = { progress.extracted / progress.total.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MaterialTheme.spacing.small),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = progress.current,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMediumEmphasized,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${progress.extracted}/${progress.total}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMediumEmphasized,
                                    modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
                                )
                            }
                        }
                    }
                }
            }
        }

        is TextToSpeechImportProgress.Error,
        is TextToSpeechImportProgress.Completed -> {
            onFinished()
        }
    }
}

@Composable
fun TextToSpeechFinishedPage(
    textToSpeechImportViewModel: TextToSpeechImportViewModel,
    innerPadding: PaddingValues
) {
    val importProgress by textToSpeechImportViewModel.importProgressState.collectAsState()

    ScaffoldBottomPage(
        innerPadding = innerPadding
    ) {
        if (importProgress is TextToSpeechImportProgress.Completed) {
            item {
                Text(
                    text = stringResource(R.string.text_to_speech_import_complete_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.text_to_speech_import_complete_explanation),
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                        .padding(
                            top = MaterialTheme.spacing.extraLarge
                        ),
                )
            }
        } else if (importProgress is TextToSpeechImportProgress.Error) {
            val import = importProgress as TextToSpeechImportProgress.Error

            item {
                Text(
                    text = stringResource(R.string.text_to_speech_import_failed_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.text_to_speech_import_failed_explanation),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                        .padding(
                            top = MaterialTheme.spacing.extraLarge
                        ),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.import_failed_extra_help),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                )
            }

            item {
                Text(
                    text = import.exception.message
                        ?: stringResource(R.string.unknown_error),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun TextToSpeechImportPreview() {
    TextToSpeechImport(
        navController = rememberNavController(),
        textToSpeechImportViewModel = TextToSpeechImportViewModel(
            modelExtractor = TarballExtractor(context = LocalContext.current),
            textToSpeechRepository = TextToSpeechMemoryRepository()
        )
    )
}