package app.versta.translate.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldModalBottomSheet(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            skipHiddenState = false,
        )
    ),
    sheetPeekHeight: Dp = BottomSheetDefaults.SheetPeekHeight,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetShape: Shape = BottomSheetDefaults.ExpandedShape,
    sheetContainerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetContentColor: Color = contentColorFor(sheetContainerColor),
    sheetTonalElevation: Dp = 0.dp,
    sheetShadowElevation: Dp = BottomSheetDefaults.Elevation,
    sheetDragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    sheetSwipeEnabled: Boolean = true,
    topBar: @Composable (() -> Unit)? = null,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable () -> Unit
) {
    val sheetScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current

    val screenHeightDp = configuration.screenHeightDp.dp
    val statusBarHeight: Dp =
        WindowInsets.safeContent.asPaddingValues().calculateTopPadding() +
                WindowInsets.safeContent.asPaddingValues().calculateBottomPadding()

    val bottomSheetMaxHeight = screenHeightDp - statusBarHeight

    val isVisible = scaffoldState.bottomSheetState.targetValue != SheetValue.Hidden
    val isExpanded = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded

    val sheetBackgroundColor by animateColorAsState(
        targetValue = if (isExpanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = TweenSpec(),
        label = "drawerBackgroundColor"
    )

    val contentPadding = WindowInsets.safeDrawing.asPaddingValues()

    BottomSheetScaffold(
        topBar = {
            Box {
                topBar?.invoke()

                Scrim(
                    color = BottomSheetDefaults.ScrimColor,
                    onDismissRequest = {
                        sheetScope.launch {
                            scaffoldState.bottomSheetState.partialExpand()
                        }
                    },
                    visible = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded,
                )
            }
        },
        content = { innerPadding ->
            val bottomPadding by animateDpAsState(
                targetValue =
                if (isVisible) (innerPadding.calculateBottomPadding())
                else (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                label = "bottom-padding"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection = LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(layoutDirection = LocalLayoutDirection.current),
                        bottom = bottomPadding
                    )
            ) {
                content()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Scrim(
                    color = BottomSheetDefaults.ScrimColor,
                    onDismissRequest = {
                        sheetScope.launch {
                            scaffoldState.bottomSheetState.partialExpand()
                        }
                    },
                    visible = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded,
                )
            }
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max = bottomSheetMaxHeight
                    ),
            ) {
                sheetContent()
            }
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetContainerColor = sheetBackgroundColor,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        sheetShape = sheetShape,
        sheetContentColor = sheetContentColor,
        sheetTonalElevation = sheetTonalElevation,
        sheetShadowElevation = sheetShadowElevation,
        sheetDragHandle = sheetDragHandle,
        sheetSwipeEnabled = sheetSwipeEnabled,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

@Composable
fun BoxScope.Scrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (color.isSpecified) {
        val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f, animationSpec = TweenSpec(),
            label = "scrim-alpha"
        )
        val closeSheet = stringResource(R.string.close_sheet)
        val dismissSheet =
            if (visible) {
                Modifier
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .semantics(mergeDescendants = true) {
                        traversalIndex = 1f
                        contentDescription = closeSheet
                        onClick {
                            onDismissRequest()
                            true
                        }
                    }
            } else {
                Modifier
            }
        Canvas(
            Modifier
                .matchParentSize()
                .then(dismissSheet)
                .then(modifier)
        ) {
            drawRect(color = color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}