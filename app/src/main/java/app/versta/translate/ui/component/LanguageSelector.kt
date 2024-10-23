package app.versta.translate.ui.component

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.LanguageType
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.databaseModule
import app.versta.translate.translateModule
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel
import org.koin.compose.KoinApplication

@Composable
fun LanguageSelector(
    languageViewModel: LanguageViewModel = koinActivityViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val sourceLanguage = languageViewModel.sourceLanguage.collectAsStateWithLifecycle(null)
    val targetLanguage = languageViewModel.targetLanguage.collectAsStateWithLifecycle(null)

    val canSwapLanguages = languageViewModel.canSwapLanguages.collectAsStateWithLifecycle(false)

    Box(
        modifier = Modifier
            .then(modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                .padding(MaterialTheme.spacing.small),
        ) {
            LanguageSelectorButton(
                context = context,
                language = sourceLanguage.value,
                text = "Source language",
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Source) },
                modifier = Modifier.weight(1f),
            )

            FilledIconButton(
                enabled = canSwapLanguages.value,
                onClick = {
                    languageViewModel.swapLanguages()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SyncAlt,
                    contentDescription = stringResource(R.string.settings),
                )
            }

            LanguageSelectorButton(
                context = context,
                language = targetLanguage.value,
                text = "Target language",
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Target) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun LanguageSelectorButton(context: Context, language: Language?, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val flagDrawable = language?.getFlagDrawable(context)

    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium, vertical = ButtonDefaults.ContentPadding.calculateTopPadding()),
        modifier = Modifier
            .height(intrinsicSize = IntrinsicSize.Min)
            .then(modifier),
    ) {
        Row (
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.fillMaxSize()
        ) {
            if (flagDrawable != null) {
                Image(
                    painter = painterResource(flagDrawable),
                    contentDescription = "Flag",
                    modifier = Modifier
                        .requiredSize(MaterialTheme.spacing.large)
                        .clip(MaterialTheme.shapes.large)
                )
            }

            Text(
                text = language?.name ?: text,
                color = MaterialTheme.colorScheme.onPrimary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Composable
@Preview
fun LanguageSelectorPreview() {
    return KoinApplication(
        application = {
            modules(
                translateModule,
                databaseModule,
            )
        }
    ) {
        LanguageSelector()
    }
}
