package app.versta.translate.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSuggestionDrawer(
    languageViewModel: LanguageViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val downloadTasks by languageViewModel.downloadTasks.collectAsStateWithLifecycle()

    val autoDetectLanguage by languageViewModel.autoDetectLanguage.collectAsStateWithLifecycle()
    val availableLanguages by languageViewModel.languageModels.collectAsStateWithLifecycle(emptyList())

    val language = availableLanguages.find { it.pair.source == autoDetectLanguage }

    if (autoDetectLanguage == null || language == null) {
        return
    }

    val onCompleteCallback by languageViewModel.languageSuggestionOnCompleteCallback.collectAsStateWithLifecycle(null)

    val task = downloadTasks.find { it.model == language }
    val status = task?.status ?: DownloadStatus.Idle

    val drawerOpenedState by languageViewModel.languageSuggestionState.collectAsStateWithLifecycle()
    val drawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!drawerOpenedState) {
        return
    }

    fun onComplete() {
        onCompleteCallback?.invoke(language)
        languageViewModel.setLanguageSuggestionState(false)
    }

    fun onDownload() {
        languageViewModel.queueDownload(context, language) {
            onComplete()
        }
    }

    fun onCancel() {
        languageViewModel.cancelDownload(context)
    }

    ModalBottomSheet(
        sheetState = drawerState,
        onDismissRequest = { languageViewModel.setLanguageSuggestionState(false) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.small)
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large)
                .then(modifier)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LanguagePairBadge(
                        pair = language.pair,
                        bidirectional = language.bidirectional
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(
                            R.string.language_suggestion_title,
                            autoDetectLanguage?.name ?: "",
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.large)
                    )
                }

                Text(
                    text = stringResource(
                        R.string.language_suggestion_description,
                        autoDetectLanguage?.name ?: "",
                    ),
                )

                Text(
                    text = stringResource(R.string.language_suggestion_call_to_action)
                )

                LanguageSuggestionDownloadButton(
                    status = status,
                    onClick = {
                        onDownload()
                    },
                    onCancel = {
                        onCancel()
                    },
                    modifier = Modifier
                        .padding(top = MaterialTheme.spacing.large)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun LanguageSuggestionDownloadButton(
    modifier: Modifier = Modifier,
    status: DownloadStatus,
    onClick: () -> Unit,
    onCancel: () -> Unit = {},
) {
    Button (
        onClick = {
            when (status) {
                is DownloadStatus.Idle,
                is DownloadStatus.Error -> onClick()

                else -> onCancel()
            }
        },
        enabled = !(status is DownloadStatus.Queued || status is DownloadStatus.Completed),
        modifier = Modifier.then(modifier)
    ) {
        Box(
            modifier = Modifier.animateContentSize(
                alignment = Alignment.Center
            )
        ) {
            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Idle,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Download"
                    )
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = null,
                    )
                }
            }

            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Queued,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queued"
                    )
                    Icon(
                        Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                    )
                }
            }


            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Progress || status is DownloadStatus.Processing,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(MaterialTheme.spacing.medium)
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                )
            }

            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Progress,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (status !is DownloadStatus.Progress) return@AnimatedVisibility
                val progress = status.downloaded.toFloat() / status.total.toFloat()

                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = MaterialTheme.spacing.hairline,
                )
            }

            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Processing,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = MaterialTheme.spacing.hairline,
                )
            }

            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Completed,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                    )
                    Text(
                        text = "Done"
                    )
                }
            }

            this@Button.AnimatedVisibility(
                visible = status is DownloadStatus.Error,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Retry"
                    )
                    Icon(
                        Icons.Outlined.Replay,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
@SuppressLint("ViewModelConstructorInComposable")
fun LanguageSuggestionDrawerPreview() {
    LanguageSuggestionDrawer(
        languageViewModel = LanguageViewModel(
            context = LocalContext.current,
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
        ).apply {
            setLanguageSuggestionState(true)
        }
    )
}
