package com.mathis.mandala.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Guardrails from docs/adr: the renderer core stays plain Kotlin so Android and the JVM tool share it. */
class ArchitectureTest {
    private val root = File(System.getProperty("user.dir")).let { if (File(it, "core").isDirectory) it else it.parentFile }

    @Test
    fun coreUsesNoPlatformApis() {
        val forbidden = listOf("import android.", "import androidx.", "import java.awt", "import javax.", "import kotlinx.coroutines")
        val sources = File(root, "core/src/main").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no core sources found under $root")
        val hits = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                forbidden.firstOrNull { line.trim().startsWith(it) }?.let { "${f.relativeTo(root)}:${i + 1}: $line" }
            }
        }
        if (hits.isNotEmpty()) fail("Platform APIs in core (ADR-0002):\n" + hits.joinToString("\n"))
    }

    @Test
    fun onlyApprovedRootFolders() {
        val approved = setOf(
            "core", "cli", "docs", "fixtures", "scripts", "tools", "gradle", ".githooks",
            ".git", ".gradle", ".idea", ".kotlin", "build", ".claude",
        )
        val extra = root.listFiles()!!.filter { it.isDirectory && it.name !in approved }.map { it.name }
        if (extra.isNotEmpty()) fail("New root folders need approval (docs/APPROVALS.md): $extra")
    }
}
