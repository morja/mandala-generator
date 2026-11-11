package com.mathis.mandala.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Entry points for Swift, which sees neither Kotlin default arguments nor fast access to a
 * `ByteArray`: settings from JSON, a render with the options the app varies, and the image as
 * RGBA bytes for a CGImage.
 */
object IosBridge {
    fun decode(json: String): MandalaParameters = SettingsJson.decode(json)

    fun render(params: MandalaParameters, size: Int, supersample: Boolean, glowReferenceWidth: Int, threads: Int): RenderResult =
        MandalaRenderer.render(
            params,
            RenderOptions(outputSize = size, supersample = supersample, glowReferenceWidth = glowReferenceWidth, threads = threads),
        )

    fun processors(): Int = availableProcessors()

    /** Runs the Kotlin/Native garbage collector, so memory readings start from a clean heap. */
    @OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
    fun collect() = kotlin.native.runtime.GC.collect()

    /**
     * A fixed collection threshold instead of the adaptive one: renders allocate many large, short-lived
     * buffers, and with autotune the heap grew to gigabytes before a collection ran (SPIKE-3).
     */
    @OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
    fun tuneGc(targetHeapMb: Int) {
        if (targetHeapMb <= 0) return
        kotlin.native.runtime.GC.autotune = false
        kotlin.native.runtime.GC.targetHeapBytes = targetHeapMb * 1024L * 1024L
    }

    @OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
    fun gcSettings(): String = with(kotlin.native.runtime.GC) {
        "autotune=$autotune targetHeapMB=${targetHeapBytes / 1048576} utilisation=$targetHeapUtilization " +
            "minHeapMB=${minHeapBytes / 1048576} maxHeapMB=${maxHeapBytes / 1048576} interval=$regularGCInterval"
    }

    /** sRGB RGBA bytes, alpha 255, top row first. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun rgba(image: Image8): NSData {
        val srgb = if (image.encoding == Encoding.SRGB) image else WorkingSpace.convertToSrgb(image)
        val n = image.width * image.height
        val out = ByteArray(n * 4)
        val src = srgb.data
        for (p in 0 until n) {
            out[p * 4] = src[p * 3]
            out[p * 4 + 1] = src[p * 3 + 1]
            out[p * 4 + 2] = src[p * 3 + 2]
            out[p * 4 + 3] = -1
        }
        return out.usePinned { NSData.create(bytes = it.addressOf(0), length = out.size.toULong()) }
    }
}
