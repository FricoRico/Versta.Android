package app.versta.translate.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel

@Composable
fun TranslatorLoadingProgressDialog(
    translationViewModel: TranslationViewModel = koinActivityViewModel()
) {
    val loadingProgress = translationViewModel.loadingProgress.collectAsStateWithLifecycle()

   if(loadingProgress.value == LoadingProgress.InProgress) {
       Dialog(onDismissRequest = { /* Can not be dismissed */ }) {
           Card(
               colors = CardDefaults.cardColors(
                   containerColor = MaterialTheme.colorScheme.surface,
               ),
               shape = MaterialTheme.shapes.extraLarge
           ) {
               CircularProgressIndicator(
                   modifier = Modifier
                       .padding(MaterialTheme.spacing.extraLarge)
                       .align(Alignment.CenterHorizontally)
               )
           }
       }
   }
}

@Composable
@Preview
fun TranslatorLoadingDialogPreview() {
    TranslatorLoadingProgressDialog()
}