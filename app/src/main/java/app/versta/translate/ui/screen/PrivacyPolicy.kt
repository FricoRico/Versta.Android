package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import app.versta.translate.R
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.theme.spacing

data class PrivacyPolicyParagraph(
    val title: String,
    val content: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicy(
    navController: NavController
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.large
    } else {
        MaterialTheme.spacing.medium
    }

    fun onBackNavigation() {
        navController.popBackStack()
    }

    val privacyPolicyContent = listOf(
        PrivacyPolicyParagraph(
            title = stringResource(R.string.information_collection_title),
            content = stringResource(R.string.information_collection_paragraph, stringResource(R.string.app_name))
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.internet_permissions_title),
            content = stringResource(R.string.internet_permission_paragraph,stringResource(R.string.app_name))
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.analytics_title),
            content = stringResource(R.string.analytics_paragraph, stringResource(R.string.app_name))
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.logging_title),
            content = stringResource(R.string.logging_paragraph,stringResource(R.string.app_name))
        ),
        PrivacyPolicyParagraph(
            title = stringResource(R.string.translation_models_title),
            content = stringResource(R.string.translation_models_paragraph, stringResource(R.string.app_name))
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

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = stringResource(R.string.privacy_policy_title),
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
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.small,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge)
            ) {
                items(items = privacyPolicyContent, key = { it.title }) {
                    PrivacyPolicyTextParagraph(paragraph = it)
                }
            }
        }
    )
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
    PrivacyPolicy(navController = NavController(LocalContext.current))
}