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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SyncAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.inbound.FilePickerCallback
import app.versta.translate.adapter.inbound.ModelFilePicker
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.core.entity.LanguageAnalysisProgress
import app.versta.translate.core.entity.LanguageImportProgress
import app.versta.translate.core.model.LanguageImportViewModel
import app.versta.translate.ui.component.ScaffoldBottomPage
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.annotateSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageImport(
    navController: NavController,
    languageImportViewModel: LanguageImportViewModel,
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
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
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
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onTertiary,
                    actionIconContentColor = MaterialTheme.colorScheme.onTertiary,
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
                        0 -> SelectionPage(
                            context,
                            coroutineScope,
                            pagerState,
                            languageImportViewModel,
                            innerPadding
                        )

                        1 -> AnalysisPage(
                            context,
                            coroutineScope,
                            pagerState,
                            languageImportViewModel,
                            innerPadding
                        )

                        2 -> ProgressPage(
                            coroutineScope,
                            pagerState,
                            languageImportViewModel,
                            innerPadding
                        )

                        3 -> FinishedPage(
                            context,
                            languageImportViewModel,
                            innerPadding
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun PageIndicator(pagerState: PagerState, modifier: Modifier = Modifier) {
    val pageCount = pagerState.pageCount
    Row(
        Modifier
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
            .then(modifier),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { iteration ->
            val color =
                if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(
                    alpha = 0.5f
                )
            Box(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.extraSmall)
                    .clip(MaterialTheme.shapes.small)
                    .background(color)
                    .weight(1f)
                    .height(MaterialTheme.spacing.extraSmall)
            )
        }
    }
}

@Composable
fun SelectionPage(
    context: Context,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    languageImportViewModel: LanguageImportViewModel,
    innerPadding: PaddingValues
) {
    val onPickExistingFile: FilePickerCallback = object : FilePickerCallback {
        override fun onFilePicked(uri: Uri) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }

            languageImportViewModel.analyze(uri)
        }
    }

    val onDownloadNewFile = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(stringResource(R.string.language_models_url, stringResource(R.string.site_url)))
    )

    ScaffoldBottomPage(
        innerPadding = innerPadding
    ) {
        item {
            Text(
                text = stringResource(R.string.language_import_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        item {
            Text(
                text = stringResource(R.string.language_import_introduction),
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
                    sentence = stringResource(R.string.language_import_file_type_explanation),
                    annotation = stringResource(R.string.language_import_file_type),
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
                    colors = ButtonDefaults.tertiaryButtonColors(),
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
fun AnalysisPage(
    context: Context,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    languageImportViewModel: LanguageImportViewModel,
    innerPadding: PaddingValues
) {
    val analysisProgress by languageImportViewModel.analysisProgressState.collectAsState()

    fun onApprove(uri: Uri) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }

        languageImportViewModel.import(uri, context.filesDir)
    }

    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels

    Box {
        AnimatedVisibility(
            visible = analysisProgress is LanguageAnalysisProgress.Idle || analysisProgress is LanguageAnalysisProgress.InProgress,
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
            visible = analysisProgress is LanguageAnalysisProgress.Error,
            enter = slideInVertically(
                initialOffsetY = { screenHeight },
            ),
            exit = slideOutVertically(
                targetOffsetY = { screenHeight },
            ),
        ) {
            val analysis = analysisProgress as LanguageAnalysisProgress.Error

            ScaffoldBottomPage(
                innerPadding = innerPadding
            ) {
                item {
                    Text(
                        text = stringResource(R.string.language_analysis_failed_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.language_analysis_failed_explanation),
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
        visible = analysisProgress is LanguageAnalysisProgress.Completed,
        enter = slideInVertically(
            initialOffsetY = { screenHeight },
        ),
        exit = slideOutVertically(
            targetOffsetY = { screenHeight },
        ),
    ) {
        val analysis = analysisProgress as LanguageAnalysisProgress.Completed

        val metadata = remember { analysis.metadata }
        val uri = remember { analysis.uri }

        val languagePairs = remember { metadata.languagePairs() }
        val distinctLanguages = remember { metadata.distinctLanguagePairs() }

        ScaffoldBottomPage(
            innerPadding = innerPadding
        ) {
            item {
                Text(
                    text = stringResource(R.string.language_analysis_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.language_analysis_explanation),
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                        .padding(
                            top = MaterialTheme.spacing.extraLarge
                        ),
                )
            }

            items(distinctLanguages, key = { it.target.isoCode }) { language ->
                val sourceFlagDrawable =
                    remember { language.source.getFlagDrawable(context) }
                val targetFlagDrawable =
                    remember { language.target.getFlagDrawable(context) }

                val isBirectional =
                    languagePairs.any { it.source == language.target && it.target == language.source }

                Box(
                    modifier = Modifier
                        .heightIn(max = MaterialTheme.spacing.extraLarge * 2)
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = -MaterialTheme.spacing.small)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(MaterialTheme.spacing.hairline)
                        ) {
                            Image(
                                painter = painterResource(sourceFlagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, language.source.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.extraLarge)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = MaterialTheme.spacing.small)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(MaterialTheme.spacing.hairline)
                        ) {
                            Image(
                                painter = painterResource(targetFlagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, language.target.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.extraLarge)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = MaterialTheme.spacing.medium)
                            .requiredSize(MaterialTheme.spacing.large)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.extraLarge
                            )
                            .padding(MaterialTheme.spacing.extraSmall)
                    ) {
                        Icon(
                            if (isBirectional) Icons.Outlined.SyncAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(96.dp)
                    ) {
                        Text(
                            text = language.source.name,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = language.target.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Button(
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.large)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                    colors = ButtonDefaults.tertiaryButtonColors(),
                    onClick = {
                        onApprove(uri)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.language_analysis_approve),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressPage(
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    languageImportViewModel: LanguageImportViewModel,
    innerPadding: PaddingValues
) {
    val importProgress by languageImportViewModel.importProgressState.collectAsState()
    fun onFinished() {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels

    when (importProgress) {
        is LanguageImportProgress.Idle, LanguageImportProgress.Started, is LanguageImportProgress.InProgress -> {
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
                    visible = importProgress is LanguageImportProgress.InProgress,
                    enter = slideInVertically(
                        initialOffsetY = { screenHeight },
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { screenHeight },
                    ),
                ) {
                    val progress = importProgress as LanguageImportProgress.InProgress

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

        is LanguageImportProgress.Error,
        is LanguageImportProgress.Completed -> {
            onFinished()
        }
    }
}

@Composable
fun FinishedPage(
    context: Context,
    languageImportViewModel: LanguageImportViewModel,
    innerPadding: PaddingValues
) {
    val importProgress by languageImportViewModel.importProgressState.collectAsState()

    ScaffoldBottomPage(
        innerPadding = innerPadding
    ) {
        if (importProgress is LanguageImportProgress.Completed) {
            val import = importProgress as LanguageImportProgress.Completed

            val languagePairs = import.metadata.bundleMetadata.languagePairs()
            val distinctLanguages = import.metadata.bundleMetadata.distinctLanguagePairs()

            item {
                Text(
                    text = stringResource(R.string.language_import_complete_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.language_import_complete_explanation),
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.medium,
                        )
                        .padding(
                            top = MaterialTheme.spacing.extraLarge
                        ),
                )
            }

            items(distinctLanguages, key = { it.target.isoCode }) { language ->
                val sourceFlagDrawable =
                    remember { language.source.getFlagDrawable(context) }
                val targetFlagDrawable =
                    remember { language.target.getFlagDrawable(context) }

                val isBirectional =
                    languagePairs.any { it.source == language.target && it.target == language.source }

                Box(
                    modifier = Modifier
                        .heightIn(max = MaterialTheme.spacing.extraLarge * 2)
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = -MaterialTheme.spacing.small)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(MaterialTheme.spacing.hairline)
                        ) {
                            Image(
                                painter = painterResource(sourceFlagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, language.source.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.extraLarge)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = MaterialTheme.spacing.small)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(MaterialTheme.spacing.hairline)
                        ) {
                            Image(
                                painter = painterResource(targetFlagDrawable),
                                contentDescription = stringResource(
                                    R.string.flag, language.target.name
                                ),
                                modifier = Modifier
                                    .requiredSize(MaterialTheme.spacing.extraLarge)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = MaterialTheme.spacing.medium)
                            .requiredSize(MaterialTheme.spacing.large)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.extraLarge
                            )
                            .padding(MaterialTheme.spacing.extraSmall)
                    ) {
                        Icon(
                            if (isBirectional) Icons.Outlined.SyncAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(96.dp)
                    ) {
                        Text(
                            text = language.source.name,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = language.target.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else if (importProgress is LanguageImportProgress.Error) {
            val import = importProgress as LanguageImportProgress.Error

            item {
                Text(
                    text = stringResource(R.string.language_import_failed_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = stringResource(R.string.language_import_failed_explanation),
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
                    text = stringResource(R.string.language_import_failed_extra_help),
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
fun LanguageImportPreview() {
    LanguageImport(
        navController = rememberNavController(),
        languageImportViewModel = LanguageImportViewModel(
            modelExtractor = TarballExtractor(context = LocalContext.current),
            languageRepository = LanguageMemoryRepository(),
        )
    )
}