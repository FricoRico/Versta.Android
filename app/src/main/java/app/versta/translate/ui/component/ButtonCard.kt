package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.darken
import app.versta.translate.utils.lighten

class ButtonCardColors(
    val containerColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val disabledContainerColor: Color = Color.Unspecified,
    val disabledContentColor: Color = Color.Unspecified,
    val iconContainerColor: Color,
    val iconColor: Color,
    val disabledIconContainerColor: Color = disabledContainerColor.darken(0.2f),
    val disabledIconColor: Color = disabledContainerColor,
) {
    @Composable
    fun buttonColors(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        )
    }
}

object ButtonCardDefaults {
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        iconContainerColor: Color = MaterialTheme.colorScheme.inverseSurface,
        iconColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        disabledIconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.darken(
            0.2f
        ),
        disabledIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) = ButtonCardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
        iconContainerColor = iconContainerColor,
        iconColor = iconColor,
        disabledIconContainerColor = disabledIconContainerColor,
        disabledIconColor = disabledIconColor,
    )

    @Composable
    fun colorsPrimary() = colors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        iconContainerColor = MaterialTheme.colorScheme.primary.lighten(0.5f),
        iconColor = MaterialTheme.colorScheme.primary,
        disabledIconContainerColor = MaterialTheme.colorScheme.primaryContainer.darken(
            0.2f
        ),
        disabledIconColor = MaterialTheme.colorScheme.primaryContainer,
    )

    @Composable
    fun colorsSecondary() = colors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        iconContainerColor = MaterialTheme.colorScheme.secondary.lighten(0.5f),
        iconColor = MaterialTheme.colorScheme.secondary,
        disabledIconContainerColor = MaterialTheme.colorScheme.secondaryContainer.darken(
            0.2f
        ),
        disabledIconColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
fun ButtonCard(
    onClick: () -> Unit = {},
    colors: ButtonCardColors = ButtonCardDefaults.colors(),
    title: String = "",
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(
            vertical = MaterialTheme.spacing.large,
            horizontal = MaterialTheme.spacing.medium
        ),
        colors = colors.buttonColors(),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .then(modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .background(
                            if (enabled) colors.iconContainerColor else colors.disabledIconContainerColor,
                            MaterialTheme.shapes.extraLarge
                        )
                        .padding(MaterialTheme.spacing.small),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (enabled) colors.iconColor else colors.disabledIconColor,
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.contentColor,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun ButtonCardPreview() {
    ButtonCard(
        onClick = {},
        colors = ButtonCardDefaults.colorsPrimary(),
        title = "Take a photo",
        subtitle = "Use your camera to take a photo",
        icon = Icons.Outlined.CameraAlt,
    )
}