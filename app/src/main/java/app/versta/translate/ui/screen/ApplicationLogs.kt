package app.versta.translate.ui.screen

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.FileSaverCallback
import app.versta.translate.adapter.outbound.LogFileSaver
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing

@Composable
fun ApplicationLogs(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    loggingViewModel: LoggingViewModel
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    val scrollState = rememberScrollState()

    val logs by loggingViewModel.logs.collectAsStateWithLifecycle()

    val onSaveLocationPicked: FileSaverCallback = object : FileSaverCallback {
        override fun onFileSaved(uri: Uri) {
            loggingViewModel.saveLogs(context, uri)
        }
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.application_logs_title))
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            IconButton(onClick = {
                loggingViewModel.clearLogs()
            }) {
                Icon(
                    ImageVector.vectorResource(R.drawable.round_delete_forever_24),
                    contentDescription = stringResource(R.string.application_logs_clear)
                )
            }

            IconButton(onClick = {
                LogFileSaver.saveFilePicker(onSaveLocationPicked)
            }) {
                Icon(
                    ImageVector.vectorResource(R.drawable.round_save_24),
                    contentDescription = stringResource(R.string.application_logs_save)
                )
            }
        },
        wrapContent = true
    ) {
        LazyColumn(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.application_logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(logs.split("\n")) {
                Text(
                    text = it,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun ApplicationLogsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    ApplicationLogs(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        loggingViewModel = LoggingViewModel(LocalContext.current.getExternalFilesDir(null))
    )
}