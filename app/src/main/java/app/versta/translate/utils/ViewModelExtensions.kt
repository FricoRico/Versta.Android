package app.versta.translate.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Suppress("UNCHECKED_CAST")
fun <T: ViewModel> viewModelFactory(factory: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            return factory() as T
        }
    }
}