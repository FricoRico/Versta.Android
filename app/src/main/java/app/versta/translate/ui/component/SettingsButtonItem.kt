package app.versta.translate.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.darken
import app.versta.translate.utils.lighten

object SettingsDefaults {
    const val DefaultDisabledAlpha = 0.38f

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        headlineColor: Color = MaterialTheme.colorScheme.onSurface,
        supportingColor: Color = headlineColor.lighten(0.2f),
        leadingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        overlineColor: Color = leadingIconColor,
        trailingIconColor: Color = leadingIconColor,
        disabledHeadlineColor: Color = headlineColor.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = disabledHeadlineColor,
        disabledTrailingIconColor: Color = disabledHeadlineColor
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
        supportingColor: Color = headlineColor.lighten(0.2f),
        leadingIconColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        overlineColor: Color = leadingIconColor,
        trailingIconColor: Color = leadingIconColor,
        disabledHeadlineColor: Color = headlineColor.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = disabledHeadlineColor,
        disabledTrailingIconColor: Color = disabledHeadlineColor,
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
        supportingColor: Color = headlineColor.lighten(0.2f),
        leadingIconColor: Color = MaterialTheme.colorScheme.onPrimary.darken(0.2f),
        overlineColor: Color = leadingIconColor,
        trailingIconColor: Color = leadingIconColor,
        disabledHeadlineColor: Color = headlineColor.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = disabledHeadlineColor,
        disabledTrailingIconColor: Color = disabledHeadlineColor,
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
        supportingColor: Color = headlineColor.lighten(0.2f),
        leadingIconColor: Color = MaterialTheme.colorScheme.onSecondary.lighten(0.2f),
        overlineColor: Color = leadingIconColor,
        trailingIconColor: Color = leadingIconColor,
        disabledHeadlineColor: Color = headlineColor.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = disabledHeadlineColor,
        disabledTrailingIconColor: Color = disabledHeadlineColor,
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
    fun colorsTertiary(
        containerColor: Color = MaterialTheme.colorScheme.tertiary,
        headlineColor: Color = MaterialTheme.colorScheme.onTertiary,
        supportingColor: Color = headlineColor.lighten(0.2f),
        leadingIconColor: Color = MaterialTheme.colorScheme.onTertiary.lighten(0.2f),
        overlineColor: Color = leadingIconColor,
        trailingIconColor: Color = leadingIconColor,
        disabledHeadlineColor: Color = headlineColor.copy(DefaultDisabledAlpha),
        disabledLeadingIconColor: Color = disabledHeadlineColor,
        disabledTrailingIconColor: Color = disabledHeadlineColor,
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
    modifier: Modifier = Modifier,
    headlineContent: String,
    onClick: (() -> Unit)? = null,
    onSwipeToDelete: (() -> Unit)? = null,
    supportingContent: String = "",
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    underlineContent: @Composable (() -> Unit)? = null,
    underlineContentPadding: PaddingValues = PaddingValues(
        start = MaterialTheme.spacing.large,
        end = MaterialTheme.spacing.large,
        bottom = MaterialTheme.spacing.large
    ),
    colors: ListItemColors = SettingsDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    index: Int = 0,
    groupSize: Int = 1,
    enabled: Boolean = true,
) {
    SettingsButtonItem(
        modifier = modifier,
        headlineContent = headlineContent,
        onClick = onClick,
        onSwipeToDelete = onSwipeToDelete,
        supportingContent = {
            if (supportingContent.isNotEmpty()) {
                Text(
                    text = supportingContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        underlineContent = underlineContent,
        underlineContentPadding = underlineContentPadding,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        index = index,
        groupSize = groupSize,
        enabled = enabled,
    )
}

@Composable
fun SettingsButtonItem(
    modifier: Modifier = Modifier,
    headlineContent: String,
    onClick: (() -> Unit)? = null,
    onSwipeToDelete: (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    underlineContent: @Composable (() -> Unit)? = null,
    underlineContentPadding: PaddingValues = PaddingValues(
        start = MaterialTheme.spacing.large,
        end = MaterialTheme.spacing.large,
        bottom = MaterialTheme.spacing.large
    ),
    colors: ListItemColors = SettingsDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    index: Int = 0,
    groupSize: Int = 1,
    enabled: Boolean = true,
) {
    val isFirstItem = remember { index == 0 }
    val isLastItem = remember { index == groupSize - 1 }

    val borderRadius = RoundedCornerShape(
        topStart = if (isFirstItem) MaterialTheme.shapes.extraLarge.topStart else MaterialTheme.shapes.small.topStart,
        topEnd = if (isFirstItem) MaterialTheme.shapes.extraLarge.topEnd else MaterialTheme.shapes.small.topEnd,
        bottomStart = if (isLastItem) MaterialTheme.shapes.extraLarge.bottomStart else MaterialTheme.shapes.small.bottomStart,
        bottomEnd = if (isLastItem) MaterialTheme.shapes.extraLarge.bottomEnd else MaterialTheme.shapes.small.bottomEnd,
    )

    val topPadding =
        if (!isFirstItem) MaterialTheme.spacing.hairline else MaterialTheme.spacing.none

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
                underlineContentPadding = underlineContentPadding,
                colors = colors,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                onClick = onClick,
                enabled = enabled,
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
                underlineContentPadding = underlineContentPadding,
                colors = colors,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                onClick = onClick,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SettingsButtonItemContent(
    headlineContent: String,
    onClick: (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)?,
    leadingContent: @Composable (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
    underlineContent: @Composable (() -> Unit)?,
    underlineContentPadding: PaddingValues,
    colors: ListItemColors,
    tonalElevation: Dp,
    shadowElevation: Dp,
    enabled: Boolean
) {
    Surface(
        color = if (enabled) colors.containerColor else colors.containerColor.darken(0.1f),
        contentColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = Modifier
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minHeight = 96.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.medium,
                Alignment.CenterVertically
            ),
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        top = MaterialTheme.spacing.large,
                        bottom = if (underlineContent != null) 0.dp else MaterialTheme.spacing.large,
                    )
                    .padding(
                        horizontal = MaterialTheme.spacing.large,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                if (leadingContent != null) {
                    leadingContent()
                }

                if (headlineContent.isNotEmpty() || supportingContent != null) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        if (headlineContent.isNotEmpty()) {
                            Text(
                                text = headlineContent,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            )
                        }

                        if (supportingContent != null) {
                            supportingContent()
                        }
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                }
            }

            if (underlineContent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(underlineContentPadding),
                ) {
                    underlineContent()
                }
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
                ImageVector.vectorResource(R.drawable.rounded_translate_24),
                contentDescription = "Localized description",
            )
        },
        onClick = {},
    )
}
