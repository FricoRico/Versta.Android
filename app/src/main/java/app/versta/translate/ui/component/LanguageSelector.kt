package app.versta.translate.ui.component

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.inbound.TranslateBubbleShortcut
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.model.LanguageType
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.ui.theme.spacing

@Composable
fun LanguageSelector(
    languageViewModel: LanguageViewModel,
    modifier: Modifier = Modifier,
    onLanguageSwap: () -> Unit = {},
) {
    val context = LocalContext.current

    val sourceLanguage by languageViewModel.sourceLanguage.collectAsStateWithLifecycle(null)
    val targetLanguage by languageViewModel.targetLanguage.collectAsStateWithLifecycle(null)

    val canSwapLanguages by languageViewModel.canSwapLanguages.collectAsStateWithLifecycle(false)

    LaunchedEffect(targetLanguage) {
        if (targetLanguage == null) {
            return@LaunchedEffect
        }

        TranslateBubbleShortcut.updateShortcutIcon(context, targetLanguage!!)
    }

    Box(
        modifier = Modifier
            .then(modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge,
                )
        ) {
            LanguageSelectorButton(
                context = context,
                language = sourceLanguage,
                text = stringResource(R.string.select_language),
                placeholder = stringResource(R.string.select_language_from),
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Source) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.extraLarge,
                    bottom = MaterialTheme.spacing.medium
                ),
                shape = RoundedCornerShape(
                    topStart = MaterialTheme.shapes.extraLarge.topStart,
                    topEnd = CornerSize(0.dp),
                    bottomStart = MaterialTheme.shapes.extraLarge.bottomStart,
                    bottomEnd = CornerSize(0.dp),
                )
            )

            LanguageSelectorButton(
                context = context,
                language = targetLanguage,
                text = stringResource(R.string.select_language),
                placeholder = stringResource(R.string.select_language_to),
                onClick = { languageViewModel.setLanguageSelectionState(LanguageType.Target) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.extraLarge,
                    top = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.medium
                ),
                shape = RoundedCornerShape(
                    topStart = CornerSize(0.dp),
                    topEnd = MaterialTheme.shapes.extraLarge.topEnd,
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = MaterialTheme.shapes.extraLarge.bottomEnd,
                )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize(),
        ) {
            val dividerColor = if (canSwapLanguages) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }

           VerticalDivider(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(MaterialTheme.spacing.small),
                color = dividerColor,
           )
            FilledIconButton(
                enabled = canSwapLanguages,
                onClick = {
                    languageViewModel.swapLanguages().invokeOnCompletion {
                        onLanguageSwap()
                    }
                },
                modifier = Modifier.align(Alignment.Center),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 1f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f),
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SyncAlt,
                    contentDescription = stringResource(R.string.swap_languages),
                )
            }
        }
    }
}

@Composable
fun LanguageSelectorButton(
    modifier: Modifier = Modifier,
    context: Context,
    language: Language?,
    text: String,
    placeholder: String,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(MaterialTheme.spacing.large, MaterialTheme.spacing.medium),
    shape: CornerBasedShape = MaterialTheme.shapes.extraLarge
) {
    val flagDrawable = language?.getFlagDrawable(context)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = shape,
        contentPadding = contentPadding,
        modifier = Modifier
            .height(intrinsicSize = IntrinsicSize.Min)
            .then(modifier),
    ) {
        Column {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(
                    bottom = MaterialTheme.spacing.small,
                    end = MaterialTheme.spacing.small,
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier.fillMaxSize()
            ) {
                if (flagDrawable != null) {
                    Image(
                        painter = painterResource(flagDrawable),
                        contentDescription = stringResource(
                            R.string.flag, language.name
                        ),
                        modifier = Modifier
                            .requiredSize(MaterialTheme.spacing.large)
                            .clip(MaterialTheme.shapes.large)
                    )
                }


                Text(
                    text = language?.name ?: text,
                    style = MaterialTheme.typography.labelLarge,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageSelectorPreview() {
    return LanguageSelector(
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        ),
    )
}
