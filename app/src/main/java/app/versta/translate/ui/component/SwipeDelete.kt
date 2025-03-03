package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.ui.theme.spacing

@Composable
fun SwipeDelete(
    onSwipeToDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = {
            with(density) {
                configuration.screenWidthDp.dp.toPx() / 2f
            }
        },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToDeleteRequested()
            }

            false
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            DeleteBackground(swipeDismissState = state)
        },
        content = { content() },
        enableDismissFromStartToEnd = false,
        modifier = modifier
    )
}

@Composable
fun DeleteBackground(
    swipeDismissState: SwipeToDismissBoxState
) {
    val color = if (swipeDismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.error
    } else Color.Transparent

    val progress = swipeDismissState.progress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(MaterialTheme.spacing.large),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = if(progress > 0.51f && progress < 0.99f) Icons.Outlined.DeleteForever else Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.remove),
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}