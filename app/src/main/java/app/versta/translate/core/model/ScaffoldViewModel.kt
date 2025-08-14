package app.versta.translate.core.model

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias ScaffoldComponent = @Composable () -> Unit
typealias ScaffoldRowScopeComponent = @Composable RowScope.() -> Unit

sealed interface ScaffoldComponentMetadata {
    val contentKey: String
    val component: ScaffoldComponent?
}

sealed interface ScaffoldRowScopeComponentMetadata {
    val contentKey: String
    val component: ScaffoldRowScopeComponent?
}

class ScaffoldTitleComponent(
    override val component: ScaffoldComponent?,
    override val contentKey: String = component.hashCode().toString(),
) : ScaffoldComponentMetadata

class ScaffoldNavigationIconComponent(
    override val component: ScaffoldComponent?,
    override val contentKey: String = component.hashCode().toString(),
) : ScaffoldComponentMetadata

class ScaffoldActionsComponent(
    override val component: ScaffoldRowScopeComponent?,
    override val contentKey: String = component.hashCode().toString(),
) : ScaffoldRowScopeComponentMetadata

class ScaffoldBottomBarComponent(
    override val component: ScaffoldComponent?,
    override val contentKey: String = component.hashCode().toString(),
) : ScaffoldComponentMetadata

data class ScaffoldComponents(
    val title: ScaffoldComponentMetadata,
    val navigationIcon: ScaffoldComponentMetadata,
    val actions: ScaffoldRowScopeComponentMetadata,
    val bottomBar: ScaffoldComponentMetadata,
    val wrapContent: Boolean,
)

class ScaffoldViewModel(
    private val navigationViewModel: NavigationViewModel,
    private val defaultTitle: ScaffoldComponentMetadata = ScaffoldTitleComponent(null),
    private val defaultNavigationIcon: ScaffoldComponentMetadata = ScaffoldNavigationIconComponent(null),
    private val defaultActions: ScaffoldRowScopeComponentMetadata = ScaffoldActionsComponent(null),
    private val defaultBottomBar: ScaffoldComponentMetadata = ScaffoldBottomBarComponent(null),
    private val defaultWrapContent: Boolean = false
) : ViewModel() {
    private val _routeScaffoldComponents: MutableMap<NavKey, ScaffoldComponents> = mutableMapOf()

    private val _title = MutableStateFlow(defaultTitle)
    val title = _title.asStateFlow()

    private val _navigationIcon = MutableStateFlow(defaultNavigationIcon)
    val navigationIcon = _navigationIcon.asStateFlow()

    private val _actions = MutableStateFlow(defaultActions)
    val actions = _actions.asStateFlow()

    private val _bottomBar = MutableStateFlow(defaultBottomBar)
    val bottomBar = _bottomBar.asStateFlow()

    private val _wrapContent = MutableStateFlow(defaultWrapContent)
    val wrapContent = _wrapContent.asStateFlow()

    /**
     * Register components for a specific route
     */
    @Synchronized
    fun registerRouteComponents(
        title: ScaffoldComponentMetadata? = null,
        navigationIcon: ScaffoldComponentMetadata? = null,
        actions: ScaffoldRowScopeComponentMetadata? = null,
        bottomBar: ScaffoldComponentMetadata? = null,
        wrapContent: Boolean = false
    ) {
        val currentRoute = navigationViewModel.navigationBackStack.lastOrNull()
        if (currentRoute == null) {
            return
        }

        val components = ScaffoldComponents(
            title = title ?: defaultTitle,
            navigationIcon = navigationIcon ?: defaultNavigationIcon,
            actions = actions ?: defaultActions,
            bottomBar = bottomBar ?: defaultBottomBar,
            wrapContent = wrapContent
        )

        _routeScaffoldComponents[currentRoute] = components
        updateActiveComponents(currentRoute)
    }

    /**
     * Update active components based on current route
     */
    private fun updateActiveComponents(currentRoute: NavKey?) {
        val components = _routeScaffoldComponents[currentRoute]
        if (components != null) {
            _title.value = components.title
            _navigationIcon.value = components.navigationIcon
            _actions.value = components.actions
            _bottomBar.value = components.bottomBar
            _wrapContent.value = components.wrapContent
            return
        }

        _title.value = defaultTitle
        _navigationIcon.value = defaultNavigationIcon
        _actions.value = defaultActions
        _bottomBar.value = defaultBottomBar
        _wrapContent.value = defaultWrapContent
    }

    init {
        navigationViewModel.registerBackNavigationListener {
            updateActiveComponents(it)
        }
    }
}