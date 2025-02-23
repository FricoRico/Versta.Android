package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.versta.translate.R
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.theme.spacing
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdParty(
    navController: NavController
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        0.dp
    }

    fun onBackNavigation() {
        navController.popBackStack()
    }

    ScaffoldLargeHeader(topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarTertiaryColor(),
        title = {
            Text(
                text = "Third-party Libraries",
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LibrariesContainer(
                modifier = Modifier
                    .nestedScroll(scrollConnection),
                padding = LibraryDefaults.libraryPadding(
                    badgePadding = PaddingValues(top = MaterialTheme.spacing.small),
                ),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                )
            )
        }
    )
}

@Composable
@Preview(showBackground = true)
fun ThirdPartyPreview() {
    ThirdParty(navController = NavController(LocalContext.current))
}