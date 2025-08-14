package app.versta.translate.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadButton(
    modifier: Modifier = Modifier,
    status: DownloadStatus,
    onClick: () -> Unit,
    onCancel: () -> Unit = {},
) {
    FilledIconButton(
        onClick = {
            when (status) {
                is DownloadStatus.Idle,
                is DownloadStatus.Error -> onClick()

                else -> onCancel()
            }
        },
        enabled = !(status is DownloadStatus.Queued || status is DownloadStatus.Completed),
        colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
        modifier = Modifier.then(modifier)
    ) {
        Box {
            AnimatedVisibility(
                visible = status is DownloadStatus.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.rounded_download_24),
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Queued,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.round_hourglass_empty_24),
                    contentDescription = null,
                )
            }


            AnimatedVisibility(
                visible = status is DownloadStatus.Progress || status is DownloadStatus.Processing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(MaterialTheme.spacing.medium)
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.rounded_stop_24),
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Progress,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (status !is DownloadStatus.Progress) return@AnimatedVisibility
                val progress = status.downloaded.toFloat() / status.total.toFloat()

                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    wavelength = MaterialTheme.spacing.small,
                    stroke = Stroke(
                        width =
                            with(LocalDensity.current) {
                                MaterialTheme.spacing.hairline.toPx()
                            },
                        cap = StrokeCap.Round,
                    ),
                    trackStroke = Stroke(
                        width =
                            with(LocalDensity.current) {
                                MaterialTheme.spacing.hairline.toPx()
                            },
                        cap = StrokeCap.Round,
                    ),
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Processing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    wavelength = MaterialTheme.spacing.small,
                    stroke = Stroke(
                        width =
                            with(LocalDensity.current) {
                                MaterialTheme.spacing.hairline.toPx()
                            },
                        cap = StrokeCap.Round,
                    ),
                    trackStroke = Stroke(
                        width =
                            with(LocalDensity.current) {
                                MaterialTheme.spacing.hairline.toPx()
                            },
                        cap = StrokeCap.Round,
                    )
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Completed,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.rounded_check_small_24),
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.round_replay_24),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageDownloadButtonPreview() {
    DownloadButton(
        onClick = { },
        status = DownloadStatus.Processing,
    )
}