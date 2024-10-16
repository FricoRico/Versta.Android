package app.versta.translate.ui.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun TranslationTextFieldMinimal(translationViewModel: TranslationViewModel = koinActivityViewModel()) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    var isTranslating by remember { mutableStateOf(false) }

    val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun translate() {
        isTranslating = true
        processingScope.launch {
            output = translationViewModel.translatorService.translate(input)

            Log.d("TranslationTextFieldMinimal", "Translated: $output")

            isTranslating = false
        }
    }


    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.small),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            TransparentTextField(
                placeholder = "Type something",
                modifier = Modifier
                    .height(128.dp)
                    .padding(MaterialTheme.spacing.small),
                value = input,
                onValueChange = {
                    input = it
                },
                onSubmit = {
                    translate()
                }
            )

            Text(
                text = output,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isTranslating) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), MaterialTheme.shapes.extraLarge)
                    .size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}