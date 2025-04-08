package app.versta.translate.ui.component

import android.icu.text.DecimalFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing

@Composable
fun DownloadButton(
    modifier: Modifier = Modifier,
    status: DownloadStatus,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = status == DownloadStatus.Idle || status is DownloadStatus.Error,
        colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
        modifier = Modifier.then(modifier)
    ) {
        Box {
            AnimatedVisibility(
                visible = status == DownloadStatus.Idle,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = status == DownloadStatus.Queued,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Icon(
                    Icons.Outlined.HourglassEmpty,
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
                visible = status == DownloadStatus.Processing,
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
                visible = status == DownloadStatus.Completed,
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
        status = DownloadStatus.Idle,
    )
}