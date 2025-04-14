package app.versta.translate.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing

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
               is DownloadStatus.Idle -> onClick()
               else -> onCancel()
           }
        },
        enabled = !(status is DownloadStatus.Queued || status is DownloadStatus.Completed),
        colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
        modifier = Modifier.then(modifier)
    ) {
        Box{
            AnimatedVisibility(
                visible = status is DownloadStatus.Idle,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Queued,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Icon(
                    Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                )
            }


            AnimatedVisibility(
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

            AnimatedVisibility(
                visible = status is DownloadStatus.Progress,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
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

            AnimatedVisibility(
                visible = status is DownloadStatus.Processing,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = MaterialTheme.spacing.hairline,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Completed,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status is DownloadStatus.Error,
            ) {
                Icon(
                    Icons.Outlined.Replay,
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