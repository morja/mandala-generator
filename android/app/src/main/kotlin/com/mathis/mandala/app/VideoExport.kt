package com.mathis.mandala.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.Parallel
import com.mathis.mandala.core.VideoFrames
import java.io.Closeable
import java.io.File

/**
 * Writes frames as an H.264 video in an MP4 file with the platform's encoder (`MediaCodec`,
 * `MediaMuxer`), so no package is needed (UX-1 12). The colour conversion is in the core
 * ([VideoFrames]); this class only hands the planes to the encoder and the encoded samples to the file.
 */
class Mp4Writer(file: File, private val width: Int, private val height: Int, private val fps: Int, threads: Int = 1) : Closeable {
    private val par = Parallel(threads)
    private val codec: MediaCodec
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var frames = 0
    private var finished = false
    private val y = ByteArray(width * height)
    private val u = ByteArray(width * height / 4)
    private val v = ByteArray(width * height / 4)

    init {
        val format = format(width, height, fps)
        val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            ?: throw UnsupportedSizeException(width, height)
        codec = MediaCodec.createByCodecName(name)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            muxer.release()
            throw e
        }
    }

    /** Adds the next frame (sRGB, `width` x `height`). */
    fun add(image: Image8) {
        require(image.width == width && image.height == height) { "Frame is ${image.width} x ${image.height}, not $width x $height." }
        VideoFrames.toI420(image, y, u, v, par)
        val index = inputIndex()
        val input = requireNotNull(codec.getInputImage(index)) { "The encoder gave no input image." }
        val planes = input.planes
        copyPlane(y, width, height, planes[0])
        copyPlane(u, width / 2, height / 2, planes[1])
        copyPlane(v, width / 2, height / 2, planes[2])
        codec.queueInputBuffer(index, 0, width * height * 3 / 2, presentationUs(frames), 0)
        frames++
        drain(endOfStream = false)
    }

    /** Ends the stream and closes the file; the video is complete only after this. */
    fun finish() {
        if (finished) return
        codec.queueInputBuffer(inputIndex(), 0, 0, presentationUs(frames), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(endOfStream = true)
        finished = true
        muxer.stop()
    }

    override fun close() {
        par.close()
        runCatching { codec.stop() }
        codec.release()
        // Stopping a muxer that never started throws; it only needs releasing then.
        runCatching { muxer.release() }
    }

    private fun inputIndex(): Int {
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) return index
            // The encoder's input is full until it hands out what it has encoded.
            drain(endOfStream = false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val data = requireNotNull(codec.getOutputBuffer(index))
                    // The codec's setup data is already in the track's format.
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        check(track >= 0) { "The encoder sent data before its format." }
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun presentationUs(frame: Int): Long = frame * 1_000_000L / fps

    /** Copies one plane into the encoder's image, whatever its row and pixel stride. */
    private fun copyPlane(src: ByteArray, w: Int, h: Int, plane: android.media.Image.Plane) {
        val buffer = plane.buffer
        val row = plane.rowStride
        val pixel = plane.pixelStride
        for (r in 0 until h) {
            if (pixel == 1) {
                buffer.position(r * row)
                buffer.put(src, r * w, w)
            } else {
                for (c in 0 until w) buffer.put(r * row + c * pixel, src[r * w + c])
            }
        }
    }

    class UnsupportedSizeException(val width: Int, val height: Int) : RuntimeException("No encoder for $width x $height")

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TIMEOUT_US = 10_000L

        /** About 0.15 bits per pixel: clean turning lines, a few MB for ten seconds. */
        private const val BITS_PER_PIXEL = 0.15

        fun format(width: Int, height: Int, fps: Int): MediaFormat = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * fps * BITS_PER_PIXEL).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }

        /** Whether this phone has an encoder for a video of this size. */
        fun supports(width: Int, height: Int, fps: Int): Boolean =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format(width, height, fps)) != null
    }
}

/** Where videos go: the shared Movies folder, or the app's cache for the share sheet. */
object VideoFiles {
    private const val FOLDER = "video"

    /** A new, empty file in the cache for the video being made; older ones are removed. */
    fun workFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, FOLDER).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, fileName)
    }

    /** Copies the video to Movies/Mandala. Needs no permission from Android 10 on. */
    fun saveToMovies(context: Context, file: File) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, MIME)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${GalleryExport.FOLDER}")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("The gallery did not accept a new video.")
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "Cannot write to the gallery." }
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** A URI other apps may read through the share sheet. */
    fun shareUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    const val MIME = "video/mp4"
}
