package app.versta.translate.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.MainApplication
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.ExtractionProgress
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.ModelFilePickerCallback
import app.versta.translate.utils.FilePicker
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.TarExtractor
import app.versta.translate.utils.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LanguageImport(
    navController: NavController,
    languageViewModel: LanguageViewModel
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary)
            .fillMaxSize()
    ) {
        Column {
            when (pagerState.currentPage) {
                0 -> SelectionScreen(
                    context,
                    coroutineScope,
                    pagerState,
                    languageViewModel
                )

                1 -> ProgressScreen(
                    coroutineScope,
                    pagerState,
                    languageViewModel
                )

                2 -> OnboardingScreen3()
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth()
    ) {
        PageIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .padding(top = MaterialTheme.spacing.large)
                .fillMaxWidth(0.7f)
        )
    }
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
fun SelectionScreen(
    context: Context,
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    languageViewmodel: LanguageViewModel
) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(stringResource(R.string.language_models_url, stringResource(R.string.site_url)))
    )

    val listener: ModelFilePickerCallback = object : ModelFilePickerCallback {
        override fun onFilePicked(uri: Uri) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
            languageViewmodel.import(uri, context.filesDir)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = "Get more Languages",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
        )
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.inverseSurface,
                    RoundedCornerShape(
                        topStart = MaterialTheme.shapes.extraLarge.topStart,
                        topEnd = MaterialTheme.shapes.extraLarge.topEnd,
                        bottomStart = CornerSize(0.dp),
                        bottomEnd = CornerSize(0.dp),
                    )
                )
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.extraLarge)
                .fillMaxWidth()
        ) {
            Text(
                text = "To import a language, you need to download a language file from our official website and import the file on your device.",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small
                ),
            )
            Text(
                text = "The files should stay in their zipped (.tar.gz) format.",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small
                ),
            )
            Column(
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.large)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Box(
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.small)
                        .clickable(
                            onClick = {
                                startActivity(context, intent, null)
                            },
                        )
                ) {
                    Text(
                        text = "I need a language file",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                    onClick = {
                        FilePicker.openFilePicker(listener)
                    },
                ) {
                    Text(
                        text = "Select file",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressScreen(
    coroutineScope: CoroutineScope,
    pagerState: PagerState,
    languageViewModel: LanguageViewModel
) {
    val progressState by languageViewModel.progressState.collectAsState()
    fun onFinished() {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

    Column {
        when (progressState) {
            is ExtractionProgress.Idle, ExtractionProgress.Started -> {
                CircularProgressIndicator()
            }

            is ExtractionProgress.InProgress -> {
                val progress = progressState as ExtractionProgress.InProgress
                // Show loader with progress
                Column {
                    Text(text = "Extracting: ${progress.current}")
                    LinearProgressIndicator(
                        progress = { progress.extracted / progress.total.toFloat() },
                    )
                    Text(text = "${progress.extracted} of ${progress.total} files extracted")
                }
            }

            is ExtractionProgress.Completed -> {
                Text(text = "Extraction completed")
                onFinished()
            }

            is ExtractionProgress.Error -> {
                val exception = (progressState as ExtractionProgress.Error).exception
                // Display error message
                Text(text = "Error: ${exception.message}")
            }
        }
    }
}

@Composable
fun OnboardingScreen3() {
    Column {
        Text("Onboarding screen 1")
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageImportPreview() {
    LanguageImport(
        navController = rememberNavController(),
        languageViewModel = LanguageViewModel(
            modelExtractor = TarExtractor(LocalContext.current),
            languageDatabaseRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        )
    )
}