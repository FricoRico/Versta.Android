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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.core.entity.Language
import app.versta.translate.utils.darken
import app.versta.translate.utils.lighten
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults

object TextFieldDefaults {
    @Composable
    fun colors(
        unfocusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedIndicatorColor: Color = Color.Transparent,
        unfocusedIndicatorColor: Color = Color.Transparent,
        focusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor: Color = focusedTextColor.lighten(0.1f),
        cursorColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor: Color = focusedTextColor.copy(alpha = 0.4f),
        focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor: Color = focusedTextColor.copy(alpha = 0.4f),
        unfocusedPlaceholderColor: Color = focusedTextColor.copy(alpha = 0.4f),
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
        unfocusedTextColor: Color = LocalTextStyle.current.color.darken(0.1f),
        focusedTextColor: Color = LocalTextStyle.current.color,
        cursorColor: Color = LocalTextStyle.current.color,
        unfocusedLabelColor: Color = MaterialTheme.colorScheme.surfaceBright,
        focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor: Color = LocalTextStyle.current.color.copy(alpha = 0.4f),
        unfocusedPlaceholderColor: Color = LocalTextStyle.current.color.copy(alpha = 0.4f),
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
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onBlur: (() -> Unit)? = null,
    sourceLocale: Language? = null,
    textStyle: TextStyle = LocalTextStyle.current,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    shape: Shape = MaterialTheme.shapes.medium,
    trailingIcon: @Composable (() -> Unit)? = null,
    value: String = "",
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        modifier = Modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocus?.invoke()
                    return@onFocusChanged
                }

                onBlur?.invoke()
            }
            .then(modifier),
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = onSubmit?.let { ImeAction.Done } ?: ImeAction.Default,
            hintLocales = sourceLocale?.let {
                LocaleList(
                    Locale(sourceLocale.isoCode)
                )
            }
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                onSubmit?.invoke()
                keyboardController?.hide()
            }),
        placeholder = {
            placeholder?.let {
                Text(
                    text = it,
                    style = textStyle,
                )
            }
        },
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        colors = colors,
        shape = shape,
    )
}

@Composable
@Preview(showBackground = true)
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