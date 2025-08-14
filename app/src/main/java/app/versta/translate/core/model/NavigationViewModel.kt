package app.versta.translate.core.model

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class NavigationViewModel(
    private val initialRoute: NavKey,
) : ViewModel() {
    private val _navigationBackStack: NavBackStack<NavKey> = NavBackStack(initialRoute)
    val navigationBackStack: NavBackStack<NavKey>
        get() = _navigationBackStack

    private var _onNavigationCallback: (() -> Unit)? = null
    private val _navigationDrawerState = MutableStateFlow(DrawerState(DrawerValue.Closed))
    val navigationDrawerState = _navigationDrawerState.asStateFlow()

    private val backNavigationListener = mutableListOf<(NavKey?) -> Unit>()

    fun navigate(route: NavKey, parent: NavKey? = null) {
        val currentParent = _navigationBackStack
            .getOrNull(_navigationBackStack.size - 2)

        if (parent != null && currentParent != null && parent::class == currentParent::class) {
            replace(route)
            return
        }

        invokeOnNavigationCallback()
        _navigationBackStack.add(route)
    }

    fun clearAndNavigate(route: NavKey, parent: NavKey? = null) {
        invokeOnNavigationCallback()

        _navigationBackStack.clear()
        if (parent != null) {
            _navigationBackStack.add(parent)
        }
        _navigationBackStack.add(route)
    }

    fun back() {
        invokeOnNavigationCallback()
        _navigationBackStack.removeLastOrNull()
        invoiceBackNavigationListener()
    }

    fun replace(route: NavKey) {
        invokeOnNavigationCallback()
        _navigationBackStack.removeLastOrNull()
        _navigationBackStack.add(route)
    }

    fun selected(route: NavKey): Boolean {
        var selected = _navigationBackStack.firstOrNull() == route
        val childRoute = _navigationBackStack.getOrNull(1)

        if (childRoute != null) {
            selected = childRoute == route
        }

        return selected
    }

    fun registerBackNavigationListener(listener: (NavKey?) -> Unit) {
        backNavigationListener.add(listener)
    }

    fun unregisterBackNavigationListener(listener: (NavKey?) -> Unit) {
        backNavigationListener.remove(listener)
    }

    fun onNavigationCallback(callback: () -> Unit) {
        _onNavigationCallback = callback
    }

    suspend fun setDrawerState(open: Boolean) {
        if (open) {
            _navigationDrawerState.value.open()
            return
        }

        _navigationDrawerState.value.close()
    }

    private fun invokeOnNavigationCallback() {
        try {
            _onNavigationCallback?.invoke()
            _onNavigationCallback = null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error invoking navigation callback")
        }
    }

    private fun invoiceBackNavigationListener() {
        val route = _navigationBackStack.lastOrNull()

        backNavigationListener.forEach { listener ->
            try {
                listener.invoke(route)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error invoking route change listener")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        backNavigationListener.clear()
    }

    companion object {
        private val TAG = NavigationViewModel::class.java.simpleName
    }
}