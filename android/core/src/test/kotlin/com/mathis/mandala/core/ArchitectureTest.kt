package com.mathis.mandala.core

import one.tearoom.testing.Guardrails
import one.tearoom.testing.Hit
import one.tearoom.testing.describe
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Guardrails from docs/adr; the scanning rules come from tearoom-android-kit (one.tearoom:testing). */
class ArchitectureTest {
    private val root = File(System.getProperty("user.dir")).let { if (File(it, "core").isDirectory) it else it.parentFile }
    private val core = Guardrails.sources(root.resolve("core/src/main").toPath())
    private val app = Guardrails.sources(root.resolve("app/src/main/kotlin").toPath())
    private val theme = setOf(Path.of("ui/theme"))

    @Test
    fun coreUsesNoPlatformApis() {
        // The renderer core stays plain Kotlin so Android and the JVM tool share it (ADR-0002).
        val forbidden = listOf("android.", "androidx.", "java.awt", "javax.", "kotlinx.coroutines")
        assertNoHits("Platform APIs in core (ADR-0002)", Guardrails.forbiddenImports(core, mapOf("" to forbidden)))
    }

    @Test
    fun appStylingUsesTokens() {
        // Colours, sizes and spacing live in ui/theme/Tokens.kt only (AGENTS.md, design tokens).
        assertNoHits("Use tokens from ui/theme/Tokens.kt instead of literals", Guardrails.rawSizes(app, theme))
        assertNoHits("Use tokens from ui/theme/Tokens.kt instead of literals", Guardrails.colourLiterals(app, theme, Regex("""Color\(0x""")))
    }

    @Test
    fun everyStringIsTranslated() {
        // The store languages next to English, across every strings*.xml.
        val locales = listOf("de", "es", "fr", "it", "ja", "ko", "pt", "ru", "zh-rCN")
        val report = Guardrails.translations(root.resolve("app/src/main/res").toPath(), locales)
        assertTrue(report.isClean, "Translations out of step:\n$report")
    }

    @Test
    fun widgetColoursMatchTheTokens() {
        // RemoteViews cannot use the Compose tokens, so values/widget.xml repeats surface, text and muted (ADR-0020).
        val widget = Regex("""<color name="widget_(\w+)_(light|dark)">#([0-9A-Fa-f]{8})</color>""")
            .findAll(root.resolve("app/src/main/res/values/widget.xml").readText())
            .associate { "${it.groupValues[2]}/${it.groupValues[1]}" to it.groupValues[3].uppercase() }
        val tokens = root.resolve("app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt").readText()
        fun token(mode: String, name: String): String {
            val block = tokens.substringAfter("val ${mode.replaceFirstChar(Char::uppercase)}Tokens = Tokens(").substringBefore(")\n")
            return Regex("""\b$name = Color\(0x([0-9A-Fa-f]{8})\)""").find(block)!!.groupValues[1].uppercase()
        }
        val expected = listOf("light", "dark").flatMap { mode ->
            listOf("$mode/surface" to token(mode, "surface"), "$mode/text" to token(mode, "text"), "$mode/muted" to token(mode, "textMuted"))
        }.toMap()
        assertEquals(expected, widget)
    }

    @Test
    fun onlyApprovedRootFolders() {
        val approved = setOf(
            "app", "core", "cli", "fixtures", "scripts", "tools", "gradle",
            ".git", ".gradle", ".idea", ".kotlin", "build", ".claude",
        )
        val extra = root.listFiles()!!.filter { it.isDirectory && it.name !in approved }.map { it.name }
        if (extra.isNotEmpty()) fail("New root folders need approval (docs/APPROVALS.md): $extra")
    }

    private fun assertNoHits(rule: String, hits: List<Hit>) {
        if (hits.isNotEmpty()) fail("$rule:\n${hits.describe()}")
    }
}
