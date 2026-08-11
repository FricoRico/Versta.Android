package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.isExpanded
import app.versta.translate.utils.isWide
import kotlinx.coroutines.launch

data class NavigationItem(
    val label: String,
    val route: Screens,
    val parent: Screens? = null,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
fun NavigationDrawer(
    modifier: Modifier = Modifier,
    navigationViewModel: NavigationViewModel,
    navigationItems: List<NavigationItem>,
    footerNavigationItems: List<NavigationItem>? = null,
    content: @Composable () -> Unit,
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()

    val scope = rememberCoroutineScope()
    val state by navigationViewModel.navigationDrawerState.collectAsStateWithLifecycle()

    fun onClickItem(screen: Screens, parent: Screens?) {
        navigationViewModel.clearAndNavigate(screen, parent)
    }

    if (windowAdaptiveInfo.isWide()) {
        return Row {
            NavigationRail(
                modifier = Modifier.then(modifier),
                containerColor = Color.Transparent,
                header = {
                    VerstaLogo(
                        modifier = Modifier.padding(
                            top = MaterialTheme.spacing.small,
                            bottom = MaterialTheme.spacing.extraLarge,
                        ),
                        size = MaterialTheme.spacing.extraLarge
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        ),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                    ) {
                        navigationItems.forEachIndexed { index, item ->
                            NavigationDrawerRailItem(
                                railExpanded = false,
                                navigationViewModel = navigationViewModel,
                                item = item,
                                onClick = { route, parent -> onClickItem(route, parent) }
                            )
                        }
                    }

                    if (windowAdaptiveInfo.isExpanded() && footerNavigationItems != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                        ) {
                            footerNavigationItems.forEachIndexed { index, item ->
                                NavigationDrawerRailItem(
                                    railExpanded = false,
                                    navigationViewModel = navigationViewModel,
                                    item = item,
                                    onClick = { route, parent -> onClickItem(route, parent) }
                                )
                            }
                        }
                    }
                }
            }

            content()
        }
    }

    ModalNavigationDrawer(
        drawerState = state,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(
                    max = 320.dp
                ),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.medium
                    ),
                ) {
                    VerstaLogo(
                        modifier = Modifier.padding(
                                top = MaterialTheme.spacing.small,
                                bottom = MaterialTheme.spacing.extraLarge,
                                start = MaterialTheme.spacing.medium
                            )
                    )

                    navigationItems.forEachIndexed { index, item ->
                        ModalDrawerItem(
                            navigationViewModel = navigationViewModel,
                            item = item,
                            onClick = { route, parent ->
                                scope.launch {
                                    onClickItem(route, parent)
                                    navigationViewModel.setDrawerState(false)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun NavigationDrawerRailItem(
    navigationViewModel: NavigationViewModel,
    railExpanded: Boolean,
    item: NavigationItem,
    onClick: (Screens, Screens?) -> Unit,
) {
    val selected = navigationViewModel.selected(item.route)

    return WideNavigationRailItem(
        railExpanded = railExpanded,
        icon = {
            val imageVector = if (selected) item.selectedIcon else item.icon
            Icon(imageVector = imageVector, contentDescription = null)
        },
        label = { Text(item.label) },
        selected = selected,
        onClick = {
            onClick(item.route, item.parent)
        },
    )
}

@Composable
fun ModalDrawerItem(
    navigationViewModel: NavigationViewModel,
    item: NavigationItem,
    onClick: (Screens, Screens?) -> Unit,
) {
    val selected = navigationViewModel.selected(item.route)

    return NavigationDrawerItem(
        icon = {
            val imageVector = if (selected) item.selectedIcon else item.icon
            Icon(imageVector = imageVector, contentDescription = null)
        },
        label = { Text(item.label) },
        selected = selected,
        onClick = {
            onClick(item.route, item.parent)
        },
    )
}