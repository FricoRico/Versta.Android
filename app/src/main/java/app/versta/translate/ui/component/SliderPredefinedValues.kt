package app.versta.translate.ui.component

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

@Composable
fun <T>SliderPredefinedValues(
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,

) {
    var index = options.indexOfFirst { it == value }

    Slider(
        modifier = modifier,
        value = index.toFloat(),
        onValueChange = {
            index = it.roundToInt()
            onValueChange(options[index])
        },
        valueRange = 0f..(options.size - 1).toFloat(),
        steps = options.size - 2,
    )
}