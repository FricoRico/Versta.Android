package app.versta.translate.ui.component

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.exp
import kotlin.math.ln

@Composable
fun SliderLogarithmic(
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    value: Float,
    minValue: Float,
    maxValue: Float,
) {
    require(minValue > 0) { "Minimum value must be greater than 0 for logarithmic scale" }
    require(minValue < maxValue) { "Minimum value must be less than maximum value" }

    val logMin = ln(minValue)
    val logMax = ln(maxValue)
    val linearValue = (ln(value) - logMin) / (logMax - logMin)

    Slider(
        modifier = modifier,
        value = linearValue,
        onValueChange = {
            onValueChange(exp(logMin + it * (logMax - logMin)))
        },
        valueRange = 0.0f..1.0f,
    )
}