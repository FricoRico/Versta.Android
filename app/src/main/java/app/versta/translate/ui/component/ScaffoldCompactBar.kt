package app.versta.translate.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldActionsComponent
import app.versta.translate.core.model.ScaffoldBottomBarComponent
import app.versta.translate.core.model.ScaffoldComponent
import app.versta.translate.core.model.ScaffoldNavigationIconComponent
import app.versta.translate.core.model.ScaffoldRowScopeComponent
import app.versta.translate.core.model.ScaffoldTitleComponent
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.isExpanded
import app.versta.translate.utils.isWide
import kotlinx.coroutines.launch

object ScaffoldCompactBarDefaults {
    @Composable
    fun topAppBarSurfaceColor(
        containerColor: Color = MaterialTheme.colorScheme.background,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onBackground,
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
    fun topAppBarTransparentColor(
        containerColor: Color = Color.Transparent,
        scrolledContainerColor: Color = containerColor,
        titleContentColor: Color = MaterialTheme.colorScheme.onBackground,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScaffoldCompactBar(
    modifier: Modifier = Modifier,
    scaffoldViewModel: ScaffoldViewModel,
    content: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()

    val titleComponent by scaffoldViewModel.title.collectAsStateWithLifecycle()
    val navigationIconComponent by scaffoldViewModel.navigationIcon.collectAsStateWithLifecycle()
    val actionsComponent by scaffoldViewModel.actions.collectAsStateWithLifecycle()
    val bottomBarComponent by scaffoldViewModel.bottomBar.collectAsStateWithLifecycle()
    val wrapContent by scaffoldViewModel.wrapContent.collectAsStateWithLifecycle()

    return Scaffold(
        modifier = Modifier.then(modifier),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                ),
                windowInsets = TopAppBarDefaults.windowInsets.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End
                ),
                colors = ScaffoldCompactBarDefaults.topAppBarTransparentColor(),
                title = {
                    Row(
                        modifier = Modifier
                            .padding(end = if (windowAdaptiveInfo.isExpanded()) MaterialTheme.spacing.medium else 0.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = titleComponent,
                            contentKey = { it.contentKey },
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = slideInVertically(
                                        animationSpec = tween(
                                            delayMillis = DefaultDurationMillis
                                        )
                                    ) { -it } + fadeIn(animationSpec = tween(delayMillis = DefaultDurationMillis)),
                                    initialContentExit = slideOutVertically { -it } + fadeOut(),
                                    sizeTransform = null
                                )
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            it.component?.invoke()
                        }
                    }
                },
                actions = {
                    if (!windowAdaptiveInfo.isWide() && actionsComponent.component == null) {
                        ScaffoldCompactBarEmptyActions()
                        return@TopAppBar
                    }

                    if (actionsComponent.component == null) {
                        return@TopAppBar
                    }

                    AnimatedContent(
                        targetState = actionsComponent,
                        contentKey = { it.contentKey },
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(
                                    animationSpec = tween(
                                        delayMillis = DefaultDurationMillis
                                    )
                                ) { it } + fadeIn(animationSpec = tween(delayMillis = DefaultDurationMillis)),
                                initialContentExit = slideOutHorizontally { it } + fadeOut(),
                                sizeTransform = null
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Row {
                            it.component?.invoke(this@TopAppBar)
                        }
                    }
                },
                navigationIcon = {
                    if (!windowAdaptiveInfo.isWide() && navigationIconComponent.component == null) {
                        ScaffoldCompactBarEmptyActions()
                        return@TopAppBar
                    }

                    if (navigationIconComponent.component == null) {
                        return@TopAppBar
                    }

                    AnimatedContent(
                        targetState = navigationIconComponent,
                        contentKey = { it.contentKey },
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(
                                    animationSpec = tween(
                                        delayMillis = DefaultDurationMillis
                                    )
                                ) { -it } + fadeIn(animationSpec = tween(delayMillis = DefaultDurationMillis)),
                                initialContentExit = slideOutHorizontally { -it } + fadeOut(),
                                sizeTransform = null
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        it.component?.invoke()
                    }
                },
            )
        },
        content = { innerPadding ->
            ScaffoldCompactContentWrapping(
                wrapContent = wrapContent,
                innerPadding = innerPadding,
                content = content
            )
        },
        bottomBar = {
            AnimatedContent(
                targetState = bottomBarComponent,
                contentKey = { it.contentKey },
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInVertically { it } + fadeIn(),
                        initialContentExit = slideOutVertically { it } + fadeOut(),
                        sizeTransform = null
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                it.component?.invoke()
            }
        }
    )
}

@Composable
fun ScaffoldCompactContentWrapping(
    wrapContent: Boolean,
    innerPadding: PaddingValues,
    content: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val squaredCornerShape = remember { RoundedCornerShape(0.dp) }

    val shouldWrap = windowAdaptiveInfo.isWide() && wrapContent

    if (shouldWrap) {
        Box(
            modifier = Modifier.padding(
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection = layoutDirection),
            )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    topEnd = squaredCornerShape.topEnd,
                    bottomStart = squaredCornerShape.bottomStart,
                    bottomEnd = squaredCornerShape.bottomEnd
                ),
            ) {
                content(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
            }
        }
    } else {
        content(innerPadding)
    }
}

@Composable
fun ScaffoldCompactBarTitle(
    text: String,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
fun ScaffoldCompactBarBackNavigationIcon(
    navigationViewModel: NavigationViewModel,
) {
    IconButton(onClick = {
        navigationViewModel.back()
    }) {
        Icon(ImageVector.vectorResource(R.drawable.rounded_arrow_back_24), stringResource(R.string.back))
    }
}

@Composable
fun ScaffoldCompactBarMenuNavigationIcon(
    navigationViewModel: NavigationViewModel,
) {
    val scope = rememberCoroutineScope()
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()

    if (windowAdaptiveInfo.isWide()) {
        return ScaffoldCompactBarEmptyActions()
    }

    IconButton(
        onClick = {
            scope.launch {
                navigationViewModel.setDrawerState(true)
            }
        }
    ) {
        Icon(ImageVector.vectorResource(R.drawable.rounded_menu_24), "Navigation menu")
    }
}

@Composable
fun ScaffoldCompactBarSettingsActions(
    navigationViewModel: NavigationViewModel,
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()

    if (windowAdaptiveInfo.isExpanded()) {
        return ScaffoldCompactBarEmptyActions()
    }

    IconButton(onClick = {
        navigationViewModel.clearAndNavigate(
            Screens.Settings,
            navigationViewModel.navigationBackStack.lastOrNull()
        )
    }) {
        Icon(ImageVector.vectorResource(R.drawable.round_settings_24), stringResource(R.string.settings))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScaffoldCompactBarEmptyActions() {
    Box(
        modifier = Modifier.size(
            size = IconButtonDefaults.smallContainerSize().plus(
                DpSize(
                    width = MaterialTheme.spacing.small,
                    height = MaterialTheme.spacing.small
                )
            )
        )
    )
}

@Composable
@Preview(showBackground = true)
fun ScaffoldCompactBarPreview() {
    ScaffoldCompactBar(
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = NavigationViewModel(Screens.TextTranslation),
            defaultTitle = ScaffoldTitleComponent({ Text(text = "Compact Bar") }),
            defaultActions = ScaffoldActionsComponent({
                IconButton(onClick = {}) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.rounded_search_24),
                        contentDescription = "Search"
                    )
                }
            }),
            defaultNavigationIcon = ScaffoldNavigationIconComponent({
                IconButton(onClick = {}) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.rounded_menu_24),
                        contentDescription = "Menu"
                    )
                }
            }),
        ),
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                items(20) { index ->
                    Text(
                        text = "Item #$index",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(MaterialTheme.spacing.medium)
                    )
                }
            }
        })
}

@Composable
fun ScaffoldComponentProvider(
    scaffoldViewModel: ScaffoldViewModel,
    title: ScaffoldComponent? = null,
    titleContentKey: String = title.hashCode().toString(),
    navigationIcon: ScaffoldComponent? = null,
    navigationIconContentKey: String = navigationIcon?.hashCode().toString(),
    actions: ScaffoldRowScopeComponent? = null,
    actionsContentKey: String = actions?.hashCode().toString(),
    bottomBar: ScaffoldComponent? = null,
    bottomBarContentKey: String = bottomBar?.hashCode().toString(),
    wrapContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) {
        scaffoldViewModel.registerRouteComponents(
            title = title?.let {
                ScaffoldTitleComponent(
                    component = it,
                    contentKey = titleContentKey
                )
            },
            navigationIcon = navigationIcon?.let {
                ScaffoldNavigationIconComponent(
                    component = it,
                    contentKey = navigationIconContentKey
                )
            },
            actions = actions?.let {
                ScaffoldActionsComponent(
                    component = it,
                    contentKey = actionsContentKey
                )
            },
            bottomBar = bottomBar?.let {
                ScaffoldBottomBarComponent(
                    component = it,
                    contentKey = bottomBarContentKey
                )
            },
            wrapContent = wrapContent
        )
    }

    content()
}


