package com.mathis.mandala.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing

private const val SOURCE_URL = "https://github.com/morja/mandala-generator"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val ANDROID_SDK_TERMS_URL = "https://developer.android.com/studio/terms"

/** Same wording as the root README and ADR-0008. */
private const val SECTION_7_PERMISSION =
    "If you modify this Program, or any covered work, by linking or combining it with the Google Play Billing Library " +
        "or with the system and platform libraries of Android, macOS or iOS (or modified versions of those libraries), " +
        "containing parts covered by the terms of their own licences, the licensors of this Program grant you additional " +
        "permission to convey the resulting work. You may also convey the Program through app stores (such as Google Play " +
        "and the Apple App Store) whose terms of service add restrictions on use or copying."

private class Library(val name: String, val licence: String, val url: String)

private val LIBRARIES = listOf(
    Library("AndroidX and Jetpack Compose", "Apache License 2.0", APACHE_URL),
    Library("Kotlin standard library and kotlinx.coroutines", "Apache License 2.0", APACHE_URL),
    Library("kotlinx.serialization", "Apache License 2.0", APACHE_URL),
    Library("Google Play Billing Library and Google Play services", "Android Software Development Kit License", ANDROID_SDK_TERMS_URL),
    Library(
        "Firebase encoders, Google Data Transport, Guava ListenableFuture, javax.inject, JSpecify and JetBrains annotations " +
            "(used by the libraries above)",
        "Apache License 2.0",
        APACHE_URL,
    ),
)

/** App name and version, the licence, the privacy policy and the licences of the bundled libraries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val t = LocalTokens.current
    val context = LocalContext.current
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Version ${info.versionName} (${info.longVersionCode})"
    }
    val german = LocalConfiguration.current.locales[0].language == "de"
    val privacyUrl = "https://www.tearoom.one/${if (german) "de" else "en"}/apps/privacy/mandala-generator"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(EditorIcons.Close, contentDescription = "Close") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = Spacing.controlsWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Image(
                        painterResource(R.mipmap.ic_launcher_art),
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.favourite).clip(CircleShape),
                    )
                    Text("Mandala Generator", style = MaterialTheme.typography.titleLarge)
                    Text(version, style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
                }

                AboutSection("Licence") {
                    Paragraph("Copyright © 2026 Mathis Koblin")
                    Paragraph(
                        "Mandala Generator is free software: you can redistribute it and/or modify it under the terms of the " +
                            "GNU General Public License as published by the Free Software Foundation, version 3. It comes " +
                            "without any warranty.",
                    )
                    LinkLine("Source code on GitHub", SOURCE_URL)
                    LinkLine("GNU General Public License 3.0", GPL_URL)
                    Group("Additional permission", summary = "GPL section 7") {
                        Paragraph(SECTION_7_PERMISSION, muted = true)
                    }
                }

                AboutSection("Privacy") {
                    Paragraph("No account, no ads, no tracking. Your mandalas and settings stay on your device.")
                    LinkLine("Privacy policy", privacyUrl)
                }

                AboutSection("Open-source libraries") {
                    for (library in LIBRARIES) LibraryRow(library)
                    Group("Apache License 2.0", summary = "Full text") { ApacheLicenceText() }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Paragraph(text: String, muted: Boolean = false) {
    val t = LocalTokens.current
    Text(
        text,
        style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = if (muted) t.textMuted else t.text,
    )
}

/** A text link in the accent colour; opens the browser. */
@Composable
private fun LinkLine(text: String, url: String, small: Boolean = false) {
    val t = LocalTokens.current
    val styles = TextLinkStyles(SpanStyle(color = t.accent, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString { withLink(LinkAnnotation.Url(url, styles)) { append(text) } },
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LibraryRow(library: Library) {
    Column(Modifier.fillMaxWidth()) {
        Text(library.name, style = MaterialTheme.typography.bodyMedium)
        LinkLine(library.licence, library.url, small = true)
    }
}

/** The bundled licence text, with its hard line breaks joined so it wraps to the screen. */
@Composable
private fun ApacheLicenceText() {
    val context = LocalContext.current
    val paragraphs = remember {
        context.assets.open("licenses/apache-2.0.txt").bufferedReader().use { it.readText() }
            .split(Regex("""\n\s*\n"""))
            .map { p -> p.lines().joinToString(" ") { it.trim() }.trim() }
            .filter { it.isNotEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (p in paragraphs) Paragraph(p, muted = true)
    }
}
