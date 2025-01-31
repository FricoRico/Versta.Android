package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.darken
import app.versta.translate.utils.lighten

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
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            DefaultDisabledAlpha
        ),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(
            DefaultDisabledAlpha
        ),
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
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(
            DefaultDisabledAlpha
        ),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(
            DefaultDisabledAlpha
        ),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.copy(
            DefaultDisabledAlpha
        ),
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
    fun colorsPrimary(
        containerColor: Color = MaterialTheme.colorScheme.primary,
        headlineColor: Color = MaterialTheme.colorScheme.onPrimary,
        leadingIconColor: Color = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
        overlineColor: Color = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
        supportingColor: Color = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
        trailingIconColor: Color = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.onPrimary.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onPrimary.copy(
            DefaultDisabledAlpha
        ),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onPrimary.copy(
            DefaultDisabledAlpha
        ),
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
        disabledHeadlineColor: Color = MaterialTheme.colorScheme.onSecondary.copy(
            DefaultDisabledAlpha
        ),
        disabledLeadingIconColor: Color = MaterialTheme.colorScheme.onSecondary.copy(
            DefaultDisabledAlpha
        ),
        disabledTrailingIconColor: Color = MaterialTheme.colorScheme.onSecondary.copy(
            DefaultDisabledAlpha
        ),
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
fun SettingsButtonItem(
    headlineContent: String,
    onClick: (() -> Unit)? = null,
    onSwipeToDelete: (() -> Unit)? = null,
    supportingContent: String = "",
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    underlineContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: ListItemColors = SettingsDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    index: Int = 0,
    groupSize: Int = 1,
) {
    val isFirstItem = remember { index == 0 }
    val isLastItem = remember { index == groupSize - 1 }

    val borderRadius = RoundedCornerShape(
        topStart = if (isFirstItem) MaterialTheme.shapes.extraLarge.topStart else MaterialTheme.shapes.medium.topStart,
        topEnd = if (isFirstItem) MaterialTheme.shapes.extraLarge.topEnd else MaterialTheme.shapes.medium.topEnd,
        bottomStart = if (isLastItem) MaterialTheme.shapes.extraLarge.bottomStart else MaterialTheme.shapes.medium.bottomStart,
        bottomEnd = if (isLastItem) MaterialTheme.shapes.extraLarge.bottomEnd else MaterialTheme.shapes.medium.bottomEnd,
    )

    val topPadding =
        if (!isFirstItem) MaterialTheme.spacing.extraSmall else MaterialTheme.spacing.none

    if (onSwipeToDelete != null) {
        SwipeDelete(
            onSwipeToDeleteRequested = onSwipeToDelete,
            modifier = Modifier
                .padding(top = topPadding)
                .clip(borderRadius)
                .then(modifier),
        ) {
            SettingsButtonItemContent(
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                underlineContent = underlineContent,
                colors = colors,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                onClick = onClick,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .clip(borderRadius)
                .then(modifier),
        ) {
            SettingsButtonItemContent(
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                underlineContent = underlineContent,
                colors = colors,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun SettingsButtonItemContent(
    headlineContent: String,
    onClick: (() -> Unit)? = null,
    supportingContent: String = "",
    leadingContent: @Composable (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
    underlineContent: @Composable (() -> Unit)?,
    colors: ListItemColors,
    tonalElevation: Dp,
    shadowElevation: Dp,
) {
    Surface(
        color = colors.containerColor,
        contentColor = colors.headlineColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minHeight = 96.dp)
                .fillMaxWidth()
                .padding(
                    vertical = MaterialTheme.spacing.large,
                    horizontal = MaterialTheme.spacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                if (leadingContent != null) {
                    leadingContent()
                }

                if (headlineContent.isNotEmpty() || supportingContent.isNotEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        if (headlineContent.isNotEmpty()) {
                            Text(
                                text = headlineContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.headlineColor,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            )
                        }

                        if (supportingContent.isNotEmpty()) {
                            Text(
                                text = supportingContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.supportingTextColor,
                            )
                        }
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                }
            }

            if (underlineContent != null) {
                underlineContent()
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun SettingsButtonItemPreview() {
    SettingsButtonItem(
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
