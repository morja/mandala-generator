package com.mathis.mandala.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathis.mandala.app.ui.AboutScreen
import com.mathis.mandala.app.ui.EditorScreen
import com.mathis.mandala.app.ui.theme.MandalaTheme

class MainActivity : ComponentActivity() {
    /**
     * The favourite of the home screen widget that was tapped (ADR-0020, amended 5 November 2026). The
     * app opens as it was; the editor only offers this one in a message, once.
     */
    private var widgetFavourite by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        widgetFavourite = intent.getStringExtra(MandalaWidget.EXTRA_FAVOURITE_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not again after a rotation, nor when Android brings back the widget's old intent from the recent apps.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) widgetFavourite = intent.getStringExtra(MandalaWidget.EXTRA_FAVOURITE_ID)
        val appearance = Appearance(this)
        // Every start counts as an opening for the rating prompt; one day counts once.
        Review.observe(this)
        setContent {
            var light by remember { mutableStateOf(appearance.light) }
            // Status and navigation bar icons follow the app's theme, not the phone's setting.
            DisposableEffect(light) {
                val style = if (light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MandalaTheme(dark = !light) {
                // The About screen lies over the editor, so the editor keeps its open tab and groups.
                var about by rememberSaveable { mutableStateOf(false) }
                Box(if (about) Modifier.clearAndSetSemantics {} else Modifier) {
                    val vm: EditorViewModel = viewModel()
                    LaunchedEffect(widgetFavourite) {
                        widgetFavourite?.let { vm.offerWidgetFavourite(it); widgetFavourite = null }
                    }
                    EditorScreen(
                        vm,
                        onAbout = { about = true },
                        covered = about,
                        lightTheme = light,
                        onLightTheme = {
                            light = it
                            appearance.light = it
                            // Widgets "like the app" change colour with it.
                            WidgetRedraw.requestChanged(this@MainActivity)
                        },
                    )
                }
                if (about) AboutScreen(onClose = { about = false })
            }
        }
    }
}
