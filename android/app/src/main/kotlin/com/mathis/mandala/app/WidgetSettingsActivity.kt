package com.mathis.mandala.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.ui.WidgetPage
import com.mathis.mandala.app.ui.WidgetSettingsScreen
import com.mathis.mandala.app.ui.theme.MandalaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One home screen widget's settings (ADR-0020, amended 5 November 2026). The launcher offers it with
 * a long press on the widget ("reconfigurable"); placing a widget does not open it
 * ("configuration_optional"), the defaults apply. The menu's Widgets page opens it too.
 */
class WidgetSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // The widget stays whatever happens here. Before Android 12 the launcher opens this page on
        // placing and leaves the first picture to it.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (savedInstanceState == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) WidgetRedraw.request(this, listOf(id))

        val store = WidgetSettingsStore.of(this)
        val appLight = Appearance(this).light
        val style = if (appLight) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)

        setContent {
            MandalaTheme(dark = !appLight) {
                var settings by remember { mutableStateOf(store.load(id)) }
                val inputs by produceState<MandalaWidget.Inputs?>(null) {
                    value = withContext(Dispatchers.IO) { MandalaWidget.Inputs.load(applicationContext) }
                }
                WidgetSettingsScreen(
                    WidgetPage(
                        title = stringResource(MandalaWidget.title(MandalaWidget.shape(options))),
                        wide = MandalaWidget.isWide(options),
                        favourites = inputs?.favorites,
                        dailyTurn = inputs?.dailyTurn,
                        pro = Pro.isUnlocked(this),
                        appLight = appLight,
                        settings = settings,
                    ),
                    words = { MandalaWidget.words(this, it.parameters) },
                    onSettings = {
                        settings = it
                        store.save(id, it)
                        WidgetRedraw.request(this, listOf(id))
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context, id: Int): Intent =
            Intent(context, WidgetSettingsActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }
}
