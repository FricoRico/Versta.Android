package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.core.model.LanguageType
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.databaseModule
import app.versta.translate.translateModule
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel
import org.koin.compose.KoinApplication

@Composable
fun LanguageSelector(modifier: Modifier = Modifier,  languageViewModel: LanguageViewModel = koinActivityViewModel()) {
    return Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(64.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.small)
            .then(modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.small),
        ) {
            Button(
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Source) },
                modifier = Modifier
                    .height(intrinsicSize = IntrinsicSize.Min)
                    .weight(1f),
            ) {
                Text(
                    text = "Source language",
                    color = MaterialTheme.colorScheme.onPrimary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }

            FilledIconButton(
                onClick = { /*TODO*/ },
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

            Button(
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Target) },
                modifier = Modifier
                    .height(intrinsicSize = IntrinsicSize.Min)
                    .weight(1f),
            ) {
                Text(
                    text = "Target language",
                    color = MaterialTheme.colorScheme.onPrimary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
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
