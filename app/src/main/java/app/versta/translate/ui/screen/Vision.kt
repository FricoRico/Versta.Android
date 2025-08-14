package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ScaffoldComponentProvider

@Composable
fun Vision(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
) {
    return ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel
    ) {
        Box(
            modifier = Modifier.consumeWindowInsets(innerPadding)
        )
    }
}