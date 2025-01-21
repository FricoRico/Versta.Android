package app.versta.translate.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.versta.translate.ui.theme.spacing

@Composable
fun Divider(
    size: Dp = MaterialTheme.spacing.medium
){
    HorizontalDivider(
        modifier = Modifier.padding(top = size),
        thickness = 0.dp,
        color = Color.Transparent
    )
}

fun LazyListScope.ListDivider(
    size: Dp = 16.dp
){
    item {
        Divider(size = size)
    }
}