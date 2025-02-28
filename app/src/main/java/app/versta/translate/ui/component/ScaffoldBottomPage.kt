package app.versta.translate.ui.component

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.versta.translate.ui.theme.spacing

data class BottomPageColors (
    val containerColor: Color,
    val contentColor: Color,
)

object ScaffoldBottomPageDefaults {

    @Composable
    fun bottomPageColors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
    ) = BottomPageColors(
        containerColor = containerColor,
        contentColor = contentColor
    )

}

@Composable
fun ScaffoldBottomPage(
    colors: BottomPageColors = ScaffoldBottomPageDefaults.bottomPageColors(),
    innerPadding: PaddingValues = PaddingValues(),
    content: LazyListScope.() -> Unit
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val lazyListState = rememberLazyListState()

    val landscapeInnerPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        0.dp
    }

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    val firstItemScrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstItemInfo by remember { derivedStateOf { lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 } } }
    val firstItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    val collapsedFraction: Float = if (firstItemIndex == 0 && firstItemInfo != null) {
        (firstItemScrollOffset / (firstItemInfo?.size?.toFloat() ?: 0f)).coerceIn(0f, 1f)
    } else {
        1f
    }

    val expandedRadius = remember { 28.dp }
    val collapsedRadius = remember { 0.dp }

    val cornerRadius = if (orientation == ORIENTATION_LANDSCAPE) {
        expandedRadius
    } else {
        (collapsedRadius + (expandedRadius - collapsedRadius) * (1 - collapsedFraction))
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(
                    layoutDirection = LocalLayoutDirection.current
                ) + landscapeInnerPadding,
                end = innerPadding.calculateEndPadding(
                    layoutDirection = LocalLayoutDirection.current
                ) + landscapeInnerPadding,
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        ContentColor(
            contentColor = colors.contentColor,
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                contentPadding = PaddingValues(
                    start = landscapeContentPadding,
                    end = landscapeContentPadding,
                    top = MaterialTheme.spacing.extraLarge + MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.extraLarge + innerPadding.calculateBottomPadding()
                ),
                modifier = Modifier
                    .background(
                        color = colors.containerColor,
                        shape = RoundedCornerShape(
                            topStart = CornerSize(cornerRadius),
                            topEnd = CornerSize(cornerRadius),
                            bottomStart = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp),
                        )
                    )
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}