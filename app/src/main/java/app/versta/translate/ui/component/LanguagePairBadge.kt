package app.versta.translate.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.R
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.ui.theme.spacing

class LanguagePairBadgeColors (
    val borderColor: Color,
    val badgeColor: Color,
    val badgeContentColor: Color
)

object LanguagePairBadgeDefaults {
    val colors: Color = Color.Unspecified

    @Composable fun colors(
        borderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        badgeColor: Color = MaterialTheme.colorScheme.surface,
        badgeContentColor: Color = MaterialTheme.colorScheme.onSurface
    ): LanguagePairBadgeColors =
        LanguagePairBadgeColors(
            borderColor = borderColor,
            badgeColor = badgeColor,
            badgeContentColor = badgeContentColor
        )
}

@Composable
fun LanguagePairBadge(
    modifier: Modifier = Modifier,
    pair: LanguagePair,
    bidirectional: Boolean,
    colors: LanguagePairBadgeColors = LanguagePairBadgeDefaults.colors()
) {
    val context = LocalContext.current
    val sourceFlagDrawable =
        remember { pair.source.getFlagDrawable(context) }
    val targetFlagDrawable =
        remember { pair.target.getFlagDrawable(context) }

    return Box(
        modifier = Modifier
            .heightIn(max = MaterialTheme.spacing.extraLarge * 2)
            .width(width = MaterialTheme.spacing.extraLarge + MaterialTheme.spacing.extraSmall * 2)
            .then(modifier),
    ) {
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = MaterialTheme.spacing.extraSmall)
                    .background(
                        color = colors.borderColor,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    .padding(MaterialTheme.spacing.hairline)
            ) {
                Image(
                    painter = painterResource(targetFlagDrawable),
                    contentDescription = stringResource(
                        R.string.flag, pair.target.name
                    ),
                    modifier = Modifier
                        .requiredSize(MaterialTheme.spacing.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = -MaterialTheme.spacing.extraSmall)
                    .background(
                        color = colors.borderColor,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                    .padding(MaterialTheme.spacing.hairline)
            ) {
                Image(
                    painter = painterResource(sourceFlagDrawable),
                    contentDescription = stringResource(
                        R.string.flag, pair.source.name
                    ),
                    modifier = Modifier
                        .requiredSize(MaterialTheme.spacing.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = MaterialTheme.spacing.medium)
                .requiredSize(MaterialTheme.spacing.large)
                .background(
                    color = colors.badgeColor,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(MaterialTheme.spacing.extraSmall)
        ) {
            Icon(
                if (bidirectional) Icons.Outlined.SyncAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = colors.badgeContentColor,
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun LanguagePairBadgePreview() {
    LanguagePairBadge(
        pair = LanguagePair.fromIsoCodes("en", "nl"),
        bidirectional = false
    )
}