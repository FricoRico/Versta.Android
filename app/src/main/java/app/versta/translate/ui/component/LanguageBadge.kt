package app.versta.translate.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import app.versta.translate.R
import app.versta.translate.core.entity.Language
import app.versta.translate.ui.theme.spacing

class LanguageBadgeColors(
    val borderColor: Color,
)

object LanguageBadgeDefaults {
    val colors: Color = Color.Unspecified

    @Composable
    fun colors(
        borderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ): LanguageBadgeColors =
        LanguageBadgeColors(
            borderColor = borderColor,
        )
}

@Composable
fun LanguageBadge(
    modifier: Modifier = Modifier,
    language: Language,
    colors: LanguageBadgeColors = LanguageBadgeDefaults.colors(),
    size: Dp = MaterialTheme.spacing.extraLarge,
    borderSize: Dp = MaterialTheme.spacing.hairline,
) {
    val context = LocalContext.current
    val flagDrawable =
        remember { language.getFlagDrawable(context).takeIf { it != 0 } ?: R.drawable.rounded_translate_24 }

    return Box(
        modifier = Modifier
            .background(
                color = colors.borderColor,
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(borderSize)
            .then(modifier)
    ) {
        Image(
            painter = painterResource(flagDrawable),
            contentDescription = stringResource(
                R.string.flag, language.name
            ),
            modifier = Modifier
                .requiredSize(size)
                .clip(MaterialTheme.shapes.extraLarge)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun LanguageBadgePreview() {
    LanguageBadge(
        language = Language.fromIsoCode("nl"),
    )
}