package ec.espol.inspeccionphotos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ec.espol.inspeccionphotos.ui.InspeccionPhotosApp
import ec.espol.inspeccionphotos.ui.InspeccionPhotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InspeccionPhotosTheme { InspeccionPhotosApp() }
        }
    }
}
