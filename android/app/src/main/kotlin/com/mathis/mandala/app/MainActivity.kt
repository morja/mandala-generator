package com.mathis.mandala.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MandalaTheme {
                // The About screen lies over the editor, so the editor keeps its open tab and groups.
                var about by rememberSaveable { mutableStateOf(false) }
                Box(if (about) Modifier.clearAndSetSemantics {} else Modifier) {
                    EditorScreen(viewModel(), onAbout = { about = true })
                }
                if (about) AboutScreen(onClose = { about = false })
            }
        }
    }
}
