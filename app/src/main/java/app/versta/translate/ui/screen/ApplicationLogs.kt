package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.FileSaverCallback
import app.versta.translate.adapter.outbound.LogFileSaver
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationLogs(
    navController: NavController,
    loggingViewModel: LoggingViewModel
) {
    val context = LocalContext.current
    val orientation = context.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.large
    } else {
        MaterialTheme.spacing.medium
    }

    val logs by loggingViewModel.logs.collectAsStateWithLifecycle()

    fun onBackNavigation() {
        navController.popBackStack()
    }

    val onSaveLocationPicked: FileSaverCallback = object : FileSaverCallback {
        override fun onFileSaved(uri: Uri) {
            loggingViewModel.saveLogs(context, uri)
        }
    }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = stringResource(R.string.application_logs_title),
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        actions = {
            IconButton(onClick = {
                loggingViewModel.clearLogs()
            }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.application_logs_clear))
            }

            IconButton(onClick = {
                LogFileSaver.saveFilePicker(onSaveLocationPicked)
            }) {
                Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.application_logs_save))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = MaterialTheme.spacing.small)
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
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

                items(logs.split("\n"), key = { it.hashCode() }) {
                    Text(text = it)
                }
            }
        }
    )
}

@Composable
@Preview(showBackground = true)
fun ApplicationLogsPreview() {
    ApplicationLogs(
        navController = rememberNavController(),
        loggingViewModel = LoggingViewModel(LocalContext.current.getExternalFilesDir(null))
    )
}