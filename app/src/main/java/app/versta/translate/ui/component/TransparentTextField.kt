package app.versta.translate.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

@Composable
fun TransparentTextField(
    modifier: Modifier = Modifier,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: (() -> Unit)? = null,
    value: String = "",
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
        label = {
            Text(placeholder)
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface,
            unfocusedLabelColor = MaterialTheme.colorScheme.surfaceBright,
            focusedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}