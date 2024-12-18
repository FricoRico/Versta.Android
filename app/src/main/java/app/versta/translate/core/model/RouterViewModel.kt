package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RouterViewModel : ViewModel() {
    val _navController = MutableStateFlow<NavHostController?>(null)
    val navController: StateFlow<NavHostController?> get() = _navController

    fun setNavController(navController: NavHostController) {
        _navController.value = navController
    }

    fun navigate(route: String) {
        _navController.value?.navigate(route)
    }
}