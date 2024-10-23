package app.versta.translate.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.versta.translate.utils.lighten
import app.versta.translate.utils.shift

object SettingsDefaults {
    val DefaultDisabledAlpha = 0.38f

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        headlineColor: Color = MaterialTheme.colorScheme.onSurface,
        leadingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        overlineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.onSurface.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(DefaultDisabledAlpha),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(DefaultDisabledAlpha),
    ) = ListItemDefaults.colors(
        containerColor = containerColor,
        headlineColor = headlineColor,
        leadingIconColor = leadingIconColor,
        overlineColor = overlineColor,
        supportingColor = supportingColor,
        trailingIconColor = trailingIconColor,
        disabledHeadlineColor = disabledHeadlineColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
    )

    @Composable
    fun colorsInverted(
        containerColor: Color = MaterialTheme.colorScheme.inverseSurface,
        headlineColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
        leadingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        overlineColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        supportingColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        trailingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(DefaultDisabledAlpha),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(DefaultDisabledAlpha),
    ) = ListItemDefaults.colors(
        containerColor = containerColor,
        headlineColor = headlineColor,
        leadingIconColor = leadingIconColor,
        overlineColor = overlineColor,
        supportingColor = supportingColor,
        trailingIconColor = trailingIconColor,
        disabledHeadlineColor = disabledHeadlineColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
    )

    @Composable
    fun colorsSecondary(
        containerColor: Color = MaterialTheme.colorScheme.secondary,
        headlineColor: Color = MaterialTheme.colorScheme.onSecondary,
        leadingIconColor: Color = MaterialTheme.colorScheme.onSecondary.lighten(0.2f),
        overlineColor: Color = MaterialTheme.colorScheme.onSecondary.lighten(0.2f),
        supportingColor: Color = MaterialTheme.colorScheme.onSecondary.lighten(0.2f),
        trailingIconColor: Color = MaterialTheme.colorScheme.onSecondary.lighten(0.2f),
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.onSecondary.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onSecondary.copy(DefaultDisabledAlpha),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onSecondary.copy(DefaultDisabledAlpha),
    ) = ListItemDefaults.colors(
        containerColor = containerColor,
        headlineColor = headlineColor,
        leadingIconColor = leadingIconColor,
        overlineColor = overlineColor,
        supportingColor = supportingColor,
        trailingIconColor = trailingIconColor,
        disabledHeadlineColor = disabledHeadlineColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
    )
}

@Composable
fun SettingsListItem(
    headlineContent: String,
    onClick: () -> Unit,
    supportingContent: String = "",
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: ListItemColors = SettingsDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
) {
    ListItem(
        headlineContent = {
            Text(
                text = headlineContent,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
            )
        },
        supportingContent = {
            if (supportingContent.isNotEmpty()) {
                Text(
                    text = supportingContent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        colors = colors,
        modifier = Modifier
            .defaultMinSize(minHeight = 96.dp)
            .clickable {
                onClick()
            }
            .then(modifier),
    )
}

@Composable
@Preview(showBackground = true)
private fun SettingsListItemPreview() {
    SettingsListItem(
        headlineContent = "Languages",
        supportingContent = "Import languages, download languages",
        leadingContent = {
            Icon(
                Icons.Outlined.Translate,
                contentDescription = "Localized description",
            )
        },
        onClick = {},
    )
}
