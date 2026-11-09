package iwarok.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import iwarok.app.ui.AppRoot
import iwarok.app.ui.theme.IwarokTheme
import iwarok.app.ui.util.LocaleHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the stored app language before any resources are resolved.
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as IwarokApp).graph
        setContent {
            IwarokTheme {
                AppRoot(graph)
            }
        }
    }
}
