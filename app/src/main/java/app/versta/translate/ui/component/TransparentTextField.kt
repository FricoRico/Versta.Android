package app.versta.translate.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TransparentTextField(
    modifier: Modifier = Modifier,
    placeholder: String,
    initialValue: String = ""
) {
    var text by remember { mutableStateOf(initialValue) }

    TextField(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        value = text,
        onValueChange = { text = it },
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