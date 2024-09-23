package name.ricardoismy.translate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import name.ricardoismy.translate.screens.CameraScreen
import name.ricardoismy.translate.ui.theme.TranslateTheme
import name.ricardoismy.translate.utils.Translator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val translator = Translator(baseContext)
        val inputText = "Dit is een test."
        val translatedText = translator.translate(inputText)
        println("Translated Text: $translatedText")

        enableEdgeToEdge()
        setContent {
            TranslateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}