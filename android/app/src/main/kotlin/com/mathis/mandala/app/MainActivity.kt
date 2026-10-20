package com.mathis.mandala.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathis.mandala.app.ui.EditorScreen
import com.mathis.mandala.app.ui.theme.MandalaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MandalaTheme {
                EditorScreen(viewModel())
            }
        }
    }
}
