package app.versta.translate.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.ui.theme.spacing

@Composable
fun LanguageDeletionConfirmationDialog(
    language: Language?,
    availableLanguages: List<LanguagePair>,
    onConfirmation: (Language) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    if (language == null) {
        return
    }

    val flagDrawable = remember { language.getFlagDrawable(context) }
    val targetLanguages = availableLanguages.filter { it.source == language }.map { it.target }

    AlertDialog(onDismissRequest = { onDismissRequest() },
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = stringResource(R.string.delete_language_title, language.name))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.delete_language_description, language.name),
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium)
                    )
                }

                items(targetLanguages, key = { it.locale }) { targetLanguage ->
                    val targetFlagDrawable = remember { targetLanguage.getFlagDrawable(context) }
                    val isBidirectional =
                        remember { availableLanguages.any { it.source == targetLanguage && it.target == language } }

                    Box(
                        modifier = Modifier.padding(
                            vertical = MaterialTheme.spacing.extraSmall,
                            horizontal = MaterialTheme.spacing.small
                        )
                    ) {
                        Icon(
                            if (isBidirectional) Icons.Outlined.SyncAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp),
                            contentDescription = null,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = MaterialTheme.spacing.small,
                                )
                            ) {
                                Image(
                                    painter = painterResource(flagDrawable),
                                    contentDescription = stringResource(
                                        R.string.flag, language.name
                                    ),
                                    modifier = Modifier
                                        .requiredSize(MaterialTheme.spacing.medium)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                )

                                Text(text = language.name)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = MaterialTheme.spacing.small,
                                )
                            ) {
                                Text(text = targetLanguage.name)

                                Image(
                                    painter = painterResource(targetFlagDrawable),
                                    contentDescription = stringResource(
                                        R.string.flag, targetLanguage.name
                                    ),
                                    modifier = Modifier
                                        .requiredSize(MaterialTheme.spacing.medium)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                )
                            }

                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation(language)
            }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest()
            }) {
                Text(text = stringResource(R.string.dismiss))
            }
        }
    )
}