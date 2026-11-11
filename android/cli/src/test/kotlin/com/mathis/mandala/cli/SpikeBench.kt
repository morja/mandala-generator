package com.mathis.mandala.cli

import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import java.io.File

/**
 * SPIKE-3 timing on the JVM and on an Android phone (through app_process), with the same cases,
 * sizes and repetitions as the iOS benchmark app in ios/SpikeBench. Not a test: run its main with
 * the folder ios/SpikeBench/Bench and optionally a thread count.
 */
object SpikeBench {
    data class Size(val name: String, val size: Int, val supersample: Boolean, val glow: Int)

    /** Preview as the Android editor draws it (512, supersampled, fast glow), the free and the Pro export. */
    val sizes = listOf(Size("preview512", 512, true, 800), Size("export1024", 1024, true, 1600), Size("pro2048", 2048, false, 1600))
    val cases = listOf("light", "favourite", "worst")
    const val RUNS = 3

    /** Bytes allocated by all threads so far, on HotSpot only (null on Android). */
    private fun allocated(): Long? = try {
        (java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean).totalThreadAllocatedBytes
    } catch (e: Throwable) {
        null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.getOrElse(0) { "ios/SpikeBench/Bench" })
        val threads = args.getOrNull(1)?.toInt() ?: Runtime.getRuntime().availableProcessors()
        println("SPIKE3 platform=${System.getProperty("java.vm.name")} threads=$threads")
        for (case in cases) {
            val params = SettingsJson.decode(File(dir, "$case.json").readText())
            for (s in sizes) {
                val options = RenderOptions(outputSize = s.size, supersample = s.supersample, glowReferenceWidth = s.glow, threads = threads)
                MandalaRenderer.render(params, options) // warm-up, also for the JIT
                val before = allocated()
                val times = (1..RUNS).map { MandalaRenderer.render(params, options).millis }.sorted()
                val perRender = allocated()?.let { a -> before?.let { (a - it) / RUNS / 1_048_576 } }
                println("SPIKE3 $case ${s.name} median=${times[RUNS / 2]} min=${times[0]} runs=$times allocatedMBPerRender=$perRender")
            }
        }
    }
}
