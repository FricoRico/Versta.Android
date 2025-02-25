package app.versta.translate.ui.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.utils.lighten
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults

object TextFieldDefaults {
    @Composable
    fun colors(
        unfocusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        focusedIndicatorColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        unfocusedTextColor: Color = MaterialTheme.colorScheme.onSurface.lighten(0.1f),
        focusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
        cursorColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.lighten(0.4f),
        unfocusedPlaceholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.lighten(0.4f),
    ) = MaterialTextFieldDefaults.colors(
        unfocusedContainerColor = unfocusedContainerColor,
        focusedContainerColor = focusedContainerColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        unfocusedTextColor = unfocusedTextColor,
        focusedTextColor = focusedTextColor,
        cursorColor = cursorColor,
        unfocusedLabelColor = unfocusedLabelColor,
        focusedLabelColor = focusedLabelColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor,
        focusedPlaceholderColor = focusedPlaceholderColor
    )

    @Composable
    fun colorsTransparent(
        unfocusedContainerColor: Color = Color.Transparent,
        focusedContainerColor: Color = Color.Transparent,
        focusedIndicatorColor: Color = Color.Transparent,
        unfocusedIndicatorColor: Color = Color.Transparent,
        unfocusedTextColor: Color = MaterialTheme.colorScheme.onSurface.lighten(0.1f),
        focusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
        cursorColor: Color = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor: Color = MaterialTheme.colorScheme.surfaceBright,
        focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.lighten(0.4f),
        unfocusedPlaceholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.lighten(0.4f),
    ) = colors(
        unfocusedContainerColor = unfocusedContainerColor,
        focusedContainerColor = focusedContainerColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        unfocusedTextColor = unfocusedTextColor,
        focusedTextColor = focusedTextColor,
        cursorColor = cursorColor,
        unfocusedLabelColor = unfocusedLabelColor,
        focusedLabelColor = focusedLabelColor,
        focusedPlaceholderColor = focusedPlaceholderColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor
    )

    @Composable
    fun colorsTransparentInverse(
        unfocusedContainerColor: Color = Color.Transparent,
        focusedContainerColor: Color = Color.Transparent,
        focusedIndicatorColor: Color = Color.Transparent,
        unfocusedIndicatorColor: Color = Color.Transparent,
        unfocusedTextColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.1f),
        focusedTextColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
        cursorColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
        unfocusedLabelColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.3f),
        focusedLabelColor: Color = MaterialTheme.colorScheme.inversePrimary,
        focusedPlaceholderColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.4f),
        unfocusedPlaceholderColor: Color = MaterialTheme.colorScheme.inverseOnSurface.lighten(0.4f),
    ) = colors(
        unfocusedContainerColor = unfocusedContainerColor,
        focusedContainerColor = focusedContainerColor,
        focusedIndicatorColor = focusedIndicatorColor,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        unfocusedTextColor = unfocusedTextColor,
        focusedTextColor = focusedTextColor,
        cursorColor = cursorColor,
        unfocusedLabelColor = unfocusedLabelColor,
        focusedLabelColor = focusedLabelColor,
        focusedPlaceholderColor = focusedPlaceholderColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor
    )
}

@Composable
fun TextField(
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    shape: Shape = MaterialTheme.shapes.medium,
    value: String = "",
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        modifier = Modifier
            .then(modifier),
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(
            onDone = {
                onSubmit?.invoke()
                keyboardController?.hide()
            }),
        placeholder = {
            Text(placeholder)
        },
        textStyle = textStyle,
        maxLines = maxLines,
        enabled = enabled,
        colors = colors,
        shape = shape,
    )
}

@Composable
@Preview
fun TextFieldPreview() {
    TextField(
        placeholder = "Type something",
        onValueChange = {},
        onSubmit = {},
        colors = TextFieldDefaults.colors(),
        value = "Hello, World!",
        maxLines = 12,
    )
}