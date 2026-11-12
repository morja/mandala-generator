import CoreVideo
import Foundation
@preconcurrency import MandalaCore

/// A pixel buffer handed to the engine for one frame; only the engine writes it, then the writer appends it.
struct FrameBuffer: @unchecked Sendable { let buffer: CVPixelBuffer }

/// The save sheet's renders (PLAN-4 WP-8). They run on the engine's thread like every render; exports use all
/// cores even while the phone is hot and only take longer (PLAN-4 section 3.2). Each video frame is its own
/// call, so the editor and the sheet are not blocked for the whole video.
extension Engine {
    /// The zoom of "Fill" for a mandala in a shape (about a tenth of a second).
    func fillZoom(_ params: MandalaParameters, aspect: Double) -> Double {
        IosSave.shared.fillZoom(params: params, aspect: aspect, threads: threads)
    }

    /// The saved picture; `progress` gets whole percents on the rendering thread; nil when cancelled.
    func framed(_ params: MandalaParameters, size: PixelSize, framing: Framing, textFollows: Bool, supersample: Bool, finish: Finish,
                ticket: RenderTicket, progress: @escaping @Sendable (Int) -> Void) -> Rendered? {
        IosSave.shared.renderFramed(params: params, width: size.width, height: size.height, framing: framing, textFollows: textFollows,
                                    supersample: supersample, finish: finish, threads: threads, ticket: ticket,
                                    imageLoader: loader, textRenderer: text) { percent in progress(Int(percent.int32Value)) }
    }

    /// The frame preview before its finish; nil when cancelled.
    func frame(_ params: MandalaParameters, size: PixelSize, framing: Framing, textFollows: Bool, ticket: RenderTicket) -> DrawnFrame? {
        if ticket.isCancelled() { return nil }
        return IosSave.shared.drawFrame(params: params, width: size.width, height: size.height, framing: framing, textFollows: textFollows,
                                        threads: threads, ticket: ticket, imageLoader: loader, textRenderer: text)
    }

    func finished(_ drawn: DrawnFrame, finish: Finish) -> Rendered { drawn.finished(finish: finish) }

    /// The mandala filling a portrait screen of this size, before its finish (the wallpaper page, WP-11).
    func wallpaper(_ params: MandalaParameters, size: PixelSize, ticket: RenderTicket,
                   progress: @escaping @Sendable (Int) -> Void = { _ in }) -> DrawnFrame? {
        if ticket.isCancelled() { return nil }
        return IosSave.shared.drawWallpaper(params: params, width: size.width, height: size.height, threads: threads, ticket: ticket,
                                            imageLoader: loader, textRenderer: text) { percent in progress(Int(percent.int32Value)) }
    }

    /// The lock screen with the finish and the home screen darker, from one drawing (ADR-0014).
    func pair(_ drawn: DrawnFrame, finish: Finish, homeBrightness: Double) -> WallpaperPair {
        drawn.pair(finish: finish, homeBrightness: homeBrightness)
    }

    /// The turning frame preview's renderer; its kept layer images live until `close`.
    func movingFrames(_ params: MandalaParameters, size: PixelSize, framing: Framing, textFollows: Bool, finish: Finish, ticket: RenderTicket) -> MovingFrames {
        IosSave.shared.movingFrames(params: params, width: size.width, height: size.height, framing: framing, textFollows: textFollows,
                                    finish: finish, threads: threads, ticket: ticket, imageLoader: loader, textRenderer: text)
    }

    func movingFrame(_ frames: MovingFrames, at t: Double) -> Rendered? { frames.at(t: t) }

    func close(_ frames: MovingFrames) { frames.close() }

    /// The video's frame source: core renders each frame and converts it to BT.709 limited range.
    func videoFrames(_ params: MandalaParameters, size: PixelSize, framing: Framing, textFollows: Bool, finish: Finish, seconds: Int,
                     ticket: RenderTicket) -> VideoFrameSource {
        IosSave.shared.videoFrames(params: params, width: size.width, height: size.height, framing: framing, textFollows: textFollows,
                                   finish: finish, seconds: Int32(seconds), threads: threads, ticket: ticket, imageLoader: loader, textRenderer: text)
    }

    /// Renders frame `index` straight into the encoder's NV12 buffer; false when cancelled.
    func writeFrame(_ source: VideoFrameSource, index: Int, into frame: FrameBuffer) -> Bool {
        let buffer = frame.buffer
        guard CVPixelBufferLockBaseAddress(buffer, []) == kCVReturnSuccess else { return false }
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let y = CVPixelBufferGetBaseAddressOfPlane(buffer, 0), let uv = CVPixelBufferGetBaseAddressOfPlane(buffer, 1) else { return false }
        return source.write(index: Int32(index), yPlane: y, yStride: Int32(CVPixelBufferGetBytesPerRowOfPlane(buffer, 0)),
                            uvPlane: uv, uvStride: Int32(CVPixelBufferGetBytesPerRowOfPlane(buffer, 1)))
    }

    func close(_ source: VideoFrameSource) { source.close() }

    /// After an export: the Kotlin buffers of the drawn picture or the video's frames go at once.
    func collect() { IosBridge.shared.collect() }
}
