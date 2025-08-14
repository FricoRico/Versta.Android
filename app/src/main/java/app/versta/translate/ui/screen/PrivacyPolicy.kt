package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

data class PrivacyPolicyParagraph(
    val title: String,
    val content: String
)

@Composable
fun PrivacyPolicy(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
) {
    val layoutDirection = LocalLayoutDirection.current

    val privacyPolicyContent = listOf(
        PrivacyPolicyParagraph(
            title = stringResource(R.string.latest_updates_title),
            content = stringResource(R.string.latest_updates_paragraph)
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.information_collection_title),
            content = stringResource(
                R.string.information_collection_paragraph,
                stringResource(R.string.app_name)
            )
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.internet_permissions_title),
            content = stringResource(
                R.string.internet_permission_paragraph,
                stringResource(R.string.app_name)
            )
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.analytics_title),
            content = stringResource(
                R.string.analytics_paragraph,
                stringResource(R.string.app_name)
            )
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.logging_title),
            content = stringResource(R.string.logging_paragraph, stringResource(R.string.app_name))
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.translation_models_title),
            content = stringResource(
                R.string.translation_models_paragraph,
                stringResource(R.string.app_name)
            )
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.changes_title),
            content = stringResource(R.string.changes_paragraph)
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.questions_title),
            content = stringResource(R.string.questions_paragraph)
        )
    )

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.privacy_policy_title))
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            ScaffoldCompactBarEmptyActions()
        },
        actionsContentKey = "ScaffoldCompactBarEmptyActions",
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge)
        ) {
            items(items = privacyPolicyContent, key = { it.title }) {
                PrivacyPolicyTextParagraph(paragraph = it)
            }
        }
    }
}

@Composable
fun PrivacyPolicyTextParagraph(
    paragraph: PrivacyPolicyParagraph
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = paragraph.title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = paragraph.content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
@Preview(showBackground = true)
fun PrivacyPolicyPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    PrivacyPolicy(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel,
        ),
        navigationViewModel = navigationViewModel
    )
}