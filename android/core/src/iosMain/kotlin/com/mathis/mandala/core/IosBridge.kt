package com.mathis.mandala.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.set
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Entry points for Swift, which sees neither Kotlin default arguments nor fast access to a
 * `ByteArray`: settings from JSON, a render with the options the app varies, and the image as
 * RGBA bytes for a CGImage. This is the iPhone app's one contract with core (PLAN-4 section 3.1);
 * SpikeBench uses the older calls at the top.
 */
object IosBridge {
    fun decode(json: String): MandalaParameters = SettingsJson.decode(json)

    fun render(params: MandalaParameters, size: Int, supersample: Boolean, glowReferenceWidth: Int, threads: Int): RenderResult =
        MandalaRenderer.render(
            params,
            RenderOptions(outputSize = size, supersample = supersample, glowReferenceWidth = glowReferenceWidth, threads = threads),
        )

    /** As [render], with a text renderer (the parity test's text cases, PLAN-4 section 3.11). */
    fun renderWithText(params: MandalaParameters, size: Int, supersample: Boolean, glowReferenceWidth: Int, threads: Int, textRenderer: TextRenderer?): RenderResult =
        MandalaRenderer.render(
            params,
            RenderOptions(outputSize = size, supersample = supersample, glowReferenceWidth = glowReferenceWidth, threads = threads, textRenderer = textRenderer),
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
        // Written straight into memory the NSData then owns: immutable data reaches Swift without a copy (an
        // NSMutableData would be copied when bridged), and a 2048 px save of a tall screen is about 40 MB (PLAN-4 WP-8).
        val size = (n * 4).toULong()
        val bytes = platform.posix.malloc(size) ?: error("No memory for a ${image.width} x ${image.height} picture.")
        val dst = bytes.reinterpret<kotlinx.cinterop.ByteVar>()
        val src = srgb.data
        for (p in 0 until n) {
            dst[p * 4] = src[p * 3]
            dst[p * 4 + 1] = src[p * 3 + 1]
            dst[p * 4 + 2] = src[p * 3 + 2]
            dst[p * 4 + 3] = -1
        }
        return NSData.create(bytesNoCopy = bytes, length = size, freeWhenDone = true)
    }

    // ── The iPhone app (PLAN-4 WP-1) ──

    /** Preview size and glow widths as on Android (`Editing.PREVIEW_SIZE`, `FAST_GLOW_REFERENCE`, the quick preview). */
    const val PREVIEW_SIZE = 512
    const val FAST_GLOW_REFERENCE = 800
    const val FULL_GLOW_REFERENCE = 1600
    const val QUICK_SIZE = 160
    const val QUICK_GLOW_REFERENCE = 320
    const val EXPORT_SIZE = 1024

    /** The starter the app icon was made from; a fresh install opens on it (`Favorites.FIRST_MANDALA_ID`). */
    const val FIRST_MANDALA_ID = "3F655220-7E77-466C-B250-E25740453709"

    fun encode(params: MandalaParameters): String = SettingsJson.encode(params)

    /** Null when the text is not settings or has no layer, as Android's `SettingsStore.load`. */
    fun decodeOrNull(json: String): MandalaParameters? =
        runCatching { SettingsJson.decode(json) }.getOrNull()?.takeIf { it.layers.isNotEmpty() }

    fun decodeFavorites(json: String): List<Favorite> = runCatching { SettingsJson.decodeFavorites(json) }.getOrDefault(emptyList())

    fun encodeFavorites(favorites: List<Favorite>): String = SettingsJson.encodeFavorites(favorites)

    /** A new favourite with a fresh UUID, newest first is the caller's list rule (`Favorites.add`). */
    fun makeFavorite(id: String, params: MandalaParameters, thumbnailBase64: String, nowMillis: Long): Favorite =
        Favorite(id = id, parameters = params, thumbnailData = thumbnailBase64, createdAt = Favorite.createdNow(nowMillis))

    /** A fresh layer cache for the editor's previews; the engine keeps one. */
    fun makeLayerCache(): LayerCache = LayerCache()

    /**
     * The editor's preview at 512 px with the layer cache, or null when `ticket` was cancelled
     * meanwhile (a newer change is on its way). `fast` blurs the glow at 800 px as Android's fast preview.
     */
    fun renderPreview(params: MandalaParameters, cache: LayerCache, fast: Boolean, threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?): Rendered? = renderOrNull {
        MandalaRenderer.render(
            params,
            RenderOptions(
                outputSize = PREVIEW_SIZE, threads = threads, layerCache = cache, isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer,
                glowReferenceWidth = if (fast) FAST_GLOW_REFERENCE else FULL_GLOW_REFERENCE,
            ),
        )
    }

    /** A small, rough picture while a slow change is drawn (Android's quick preview); never cached. */
    fun renderQuick(params: MandalaParameters, threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?): Rendered? = renderOrNull {
        MandalaRenderer.render(
            params,
            RenderOptions(outputSize = QUICK_SIZE, glowReferenceWidth = QUICK_GLOW_REFERENCE, threads = threads, isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer),
        )
    }

    /**
     * The square picture as saved at `size` px, supersampled as the free export on Android. `progress`
     * gets whole percents (0–100) on the rendering thread; null when `ticket` was cancelled.
     */
    fun renderSquare(params: MandalaParameters, size: Int, threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?, progress: (Int) -> Unit): Rendered? = renderOrNull {
        var shown = -1
        MandalaRenderer.render(
            params,
            RenderOptions(
                outputSize = size, supersample = true, threads = threads, isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer,
                onProgress = { share ->
                    val percent = (share * 100).toInt()
                    if (percent > shown) {
                        shown = percent
                        progress(percent)
                    }
                },
            ),
        )
    }

    /**
     * A background picture for core from sRGB RGBA bytes (`size` × `size`, alpha ignored): what Swift's
     * `ImageLoader` (ImageIO) hands back from `load`.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun image8(rgba: NSData, size: Int): Image8 {
        val out = Image8(size, size, Encoding.SRGB)
        val n = size * size
        val src = ByteArray(n * 4)
        if (rgba.length.toInt() >= src.size) src.usePinned { platform.posix.memcpy(it.addressOf(0), rgba.bytes, src.size.toULong()) }
        for (p in 0 until n) {
            out.data[p * 3] = src[p * 4]
            out.data[p * 3 + 1] = src[p * 4 + 1]
            out.data[p * 3 + 2] = src[p * 4 + 2]
        }
        return out
    }

    /**
     * A text coverage mask for core from 8-bit alpha bytes (`width` × `height`, row 0 at the top, rows
     * `bytesPerRow` apart): what Swift's Core Text `TextRenderer` draws, as `drawLines` returns it.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun coverage(alpha: NSData, width: Int, height: Int, bytesPerRow: Int): FloatArray {
        val out = FloatArray(width * height)
        if (alpha.length.toInt() < bytesPerRow * height || bytesPerRow * height <= 0) return out
        // Read in place, without a copy of the bytes (a 2048 px export would hold one more 4 MB array).
        val src = alpha.bytes!!.reinterpret<kotlinx.cinterop.UByteVar>()
        for (y in 0 until height) {
            val row = y * bytesPerRow
            val o = y * width
            for (x in 0 until width) out[o + x] = src[row + x].toInt() / 255f
        }
        return out
    }

    private inline fun renderOrNull(render: () -> RenderResult): Rendered? = try {
        val result = render()
        Rendered(rgba(result.image), result.image.width, result.image.height, result.millis)
    } catch (e: RenderCancelledException) {
        null
    }
}

/** A picture for Swift: sRGB RGBA bytes, top row first, and how long it took. */
class Rendered(val rgba: NSData, val width: Int, val height: Int, val millis: Long)

/** Lets Swift stop a running render from another thread; core polls it between steps. */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
class RenderTicket {
    private val cancelled = kotlin.concurrent.atomics.AtomicInt(0)

    fun cancel() = cancelled.store(1)

    fun isCancelled(): Boolean = cancelled.load() != 0
}
