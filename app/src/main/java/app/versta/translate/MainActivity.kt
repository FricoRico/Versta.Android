package app.versta.translate

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.versta.translate.ui.screen.Camera
import app.versta.translate.ui.theme.TranslateTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import app.versta.translate.utils.FilePicker
import app.versta.translate.ui.component.LanguageSelectionDrawer
import app.versta.translate.ui.component.Router
import app.versta.translate.ui.component.TranslatorLoadingProgressDialog

open class MainActivity : ComponentActivity() {
//    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>

//    private val fileExtractorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }

            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            setCameraPreview()
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun setCameraPreview() {
        setContent {
            TranslateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Camera(
                        modifier = Modifier
                            .padding(innerPadding)
                            .clip(shape = MaterialTheme.shapes.extraLarge)
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FilePicker.registerForActivity(this)

        enableEdgeToEdge()
        setContent {
            TranslateTheme {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ){
                    Router()

                    TranslatorLoadingProgressDialog()
                    LanguageSelectionDrawer()
                }
            }
        }
//
//        if (!allPermissionsGranted()) {
//            requestPermissions()
//        } else {
//            setCameraPreview()
//        }
    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}