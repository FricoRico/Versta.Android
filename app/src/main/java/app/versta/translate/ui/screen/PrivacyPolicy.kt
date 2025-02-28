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
            title = "Information We Collect",
            content = "Versta does not collect any personal data from its users. The app is designed to function entirely offline, and as such, it does not require an internet connection to operate."
        ),
        PrivacyPolicyParagraph(
            title = "No Internet Permissions",
            content = "Versta does not request or require any internet permissions in the Android manifest. This means the app cannot access the internet from your device, ensuring that no data is sent or received without your explicit action."
        ),
        PrivacyPolicyParagraph(
            title = "No Analytics",
            content = "Versta does not use any analytics services. We do not track or collect data about your usage of the app."
        ),
        PrivacyPolicyParagraph(
            title = "Logs",
            content = "Versta generates logs to help diagnose and fix issues. These logs are stored locally on your device and are not automatically shared with us. You have the option to export these logs and share them with us if you encounter an issue and choose to report it on our GitHub repository. The logs do not contain any personal data or translations."
        ),
        PrivacyPolicyParagraph(
            title = "Translation Models",
            content = "The translation models used in Versta are downloaded separately through your browser and are not tied to the app's functionality. This ensures that your translations remain private and are not sent to any external servers."
        ),
        PrivacyPolicyParagraph(
            title = "Changes to This Privacy Policy",
            content = "We are committed to maintaining the highest standards of privacy and will not make changes to this Privacy Policy that negatively impact your privacy rights. If we make any updates to this Privacy Policy, they will be to enhance your privacy protections or to comply with changes in the law. We will notify you of any changes by posting the updated Privacy Policy on this page. You are advised to review this Privacy Policy periodically for any updates. Changes to this Privacy Policy are effective when they are posted on this page, and we will ensure that any revisions uphold our commitment to your privacy."
        ),
        PrivacyPolicyParagraph(
            title = "Contact Us",
            content = "If you have any questions about this Privacy Policy, please contact us through our GitHub repository."
        )
    )

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = "Privacy Policy",
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