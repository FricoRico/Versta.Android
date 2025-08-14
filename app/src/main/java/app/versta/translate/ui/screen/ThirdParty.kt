package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.R
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults.chipPadding
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors

@Composable
fun ThirdParty(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
) {
    val layoutDirection = LocalLayoutDirection.current
    val libraries by rememberLibraries(R.raw.aboutlibraries)

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.third_party_title))
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            ScaffoldCompactBarEmptyActions()
        },
        actionsContentKey = "ScaffoldCompactBarEmptyActions",
        wrapContent = true
    ) {
        LibrariesContainer(
            padding = LibraryDefaults.libraryPadding(
                licensePadding = chipPadding(
                    containerPadding = PaddingValues(top = MaterialTheme.spacing.small),
                )
            ),
            colors = LibraryDefaults.libraryColors(
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            ),
            libraries = libraries
        )
    }
}

@Composable
@Preview(showBackground = true)
fun ThirdPartyPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)
    ThirdParty(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
    )
}