package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import app.versta.translate.ui.theme.spacing

@Composable
fun SettingsHeaderItem(
    headlineContent: String,
    modifier: Modifier = Modifier,
    colors: ListItemColors = SettingsDefaults.colors(
        headlineColor = MaterialTheme.colorScheme.primary
    ),
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

    Box(
        modifier = Modifier
            .padding(top = topPadding)
            .clip(borderRadius)
            .then(modifier),
    ) {
        Surface(
            contentColor = colors.headlineColor,
            color = colors.containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        vertical = MaterialTheme.spacing.medium,
                        horizontal = MaterialTheme.spacing.large,
                    )
                    .fillMaxWidth()
            ) {
                Text(
                    text = headlineContent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        }
    }
}

@Composable
@Preview(showBackground = true)
fun SettingsHeaderItemPreview() {
    SettingsHeaderItem(
        headlineContent = "History",
    )
}