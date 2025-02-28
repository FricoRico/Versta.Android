package app.versta.translate.ui.component

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
object ScaffoldLargeHeaderDefaults {
    @Composable
    fun topAppBarPrimaryColor(
        containerColor: Color = MaterialTheme.colorScheme.primary,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarSecondaryColor(
        containerColor: Color = MaterialTheme.colorScheme.secondary,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onSecondary,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarTertiaryColor(
        containerColor: Color = MaterialTheme.colorScheme.tertiary,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onTertiary,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarInverseSurfaceColor(
        containerColor: Color = MaterialTheme.colorScheme.inverseSurface,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarSurfaceColor(
        containerColor: Color = MaterialTheme.colorScheme.surface,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarsurfaceContainerLowColor(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )

    @Composable
    fun topAppBarsurfaceContainerLowestColor(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor: Color = titleContentColor,
        navigationIconContentColor: Color = titleContentColor,
    ) = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        titleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor,
        navigationIconContentColor = navigationIconContentColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldLargeHeader(
    title: @Composable () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    navigationIcon: @Composable (() -> Unit) = {},
    header: @Composable ((insets: PaddingValues, scrollConnection: NestedScrollConnection) -> Unit)? = null,
    content: @Composable (insets: PaddingValues, scrollConnection: NestedScrollConnection) -> Unit = { _, _ -> },
    collapsedHeight: Dp = Dp.Unspecified,
    expandedHeight: Dp = Dp.Unspecified,
    topAppBarColors: TopAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarInverseSurfaceColor(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = contentColorFor(containerColor),
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val collapsedFraction = scrollBehavior.state.collapsedFraction

    val expandedRadius = remember { 28.dp }
    val collapsedRadius = remember { 0.dp }

    val cornerRadius = if (orientation == ORIENTATION_LANDSCAPE) {
        expandedRadius
    } else {
        (collapsedRadius + (expandedRadius - collapsedRadius) * (1 - collapsedFraction))
    }

    val roundedCornerShape = RoundedCornerShape(
        topStart = CornerSize(cornerRadius),
        topEnd = CornerSize(cornerRadius),
        bottomStart = CornerSize(0.dp),
        bottomEnd = CornerSize(0.dp),
    )

    val landscapeInnerPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        0.dp
    }

    Scaffold(
        modifier = Modifier.nestedScroll(connection = scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = title,
                actions = actions,
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                colors = topAppBarColors,
                collapsedHeight = collapsedHeight,
                expandedHeight = expandedHeight
            )
        },
        containerColor = topAppBarColors.containerColor,
        contentColor = contentColor,
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current) + landscapeInnerPadding,
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current) + landscapeInnerPadding
                    )
            ) {
                Surface(
                    color = Color.Transparent,
                    contentColor = topAppBarColors.titleContentColor
                ) {
                    header?.invoke(innerPadding, scrollBehavior.nestedScrollConnection)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (header == null) { innerPadding.calculateTopPadding() } else { 0.dp })
                        .background(
                            color = containerColor,
                            shape = roundedCornerShape
                        )
                        .clip(
                            shape = roundedCornerShape
                        )
                ) {
                    content(innerPadding, scrollBehavior.nestedScrollConnection)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun ScaffoldLargeHeaderPreview() {
    ScaffoldLargeHeader(
        title = {
            Text(
                text = "Versta",
            )
        },
        content = { insets, _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = insets.calculateTopPadding(),
                        start = insets.calculateStartPadding(LocalLayoutDirection.current),
                        end = insets.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = insets.calculateBottomPadding()
                    )
            )
        }
    )
}