import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import os

// Kotlin data classes are immutable; the ticket is atomic; a rendered picture is never changed after it is made.
extension MandalaParameters: @retroactive @unchecked Sendable {}
extension Favorite: @retroactive @unchecked Sendable {}
extension Rendered: @retroactive @unchecked Sendable {}
extension RenderTicket: @retroactive @unchecked Sendable {}
extension CustomPaletteStop: @retroactive @unchecked Sendable {}
extension CustomPalette: @retroactive @unchecked Sendable {}
extension Quote: @retroactive @unchecked Sendable {}

/// Owns every render call (PLAN-4 section 3.2). Kotlin calls block, so the actor runs on its own serial
/// queue, never on the main thread or Swift's cooperative pool; inside a render core's worker pool
/// (`Parallel`) uses all cores. The layer cache of the editor's preview lives here and nowhere else.
actor Engine {
    private let queue = DispatchSerialQueue(label: "one.tearoom.mandala.engine", qos: .userInitiated)
    nonisolated var unownedExecutor: UnownedSerialExecutor { queue.asUnownedSerialExecutor() }

    private let bridge = IosBridge.shared
    private let cache: LayerCache
    private let threads: Int32
    private let loader = ImageIOLoader()
    private let text = CoreTextRenderer()

    private static let log = Logger(subsystem: "one.tearoom.mandala", category: "engine")

    /// Timing lines for the measurements in IOS-1: to the log, and in debug builds also to the console
    /// (`devicectl … --console` shows them), each starting with DAHLIA.
    nonisolated static func trace(_ line: String) {
        log.info("DAHLIA \(line, privacy: .public)")
        #if DEBUG
        print("DAHLIA \(line)")
        #endif
    }

    /// The collector target the app starts with (PLAN-4 approval A5): 0 keeps Kotlin/Native's
    /// autotuned collector. Set to 64 only if the iPhone 15 measurement confirms the gain (IOS-1).
    static let collectorTargetMB = 0

    init() {
        cache = IosBridge.shared.makeLayerCache()
        threads = IosBridge.shared.processors()
        // Debug builds may try another target: launch with `-collectorTargetMB 64`.
        #if DEBUG
        let target = UserDefaults.standard.string(forKey: "collectorTargetMB").flatMap { Int($0) } ?? Self.collectorTargetMB
        #else
        let target = Self.collectorTargetMB
        #endif
        IosBridge.shared.tuneGc(targetHeapMb: Int32(target))
        Self.trace("engine threads=\(IosBridge.shared.processors()) gc \(IosBridge.shared.gcSettings())")
    }

    /// Worker threads for the editor's previews: all cores, or half of them while the phone is hot
    /// (PLAN-4 section 3.2, throttling). Exports always use all; they only take longer when it is hot.
    private func previewThreads(hot: Bool) -> Int32 { hot ? max(1, threads / 2) : threads }

    /// The editor's 512 px preview with the layer cache; nil when cancelled. `hot`: the phone's thermal
    /// state is serious or worse, so the caller asks for the fast glow and fewer threads are used.
    func preview(_ params: MandalaParameters, fast: Bool, hot: Bool = false, ticket: RenderTicket) -> Rendered? {
        if ticket.isCancelled() { return nil }
        return bridge.renderPreview(params: params, cache: cache, fast: fast, threads: previewThreads(hot: hot), ticket: ticket,
                                    imageLoader: loader, textRenderer: text)
    }

    /// A small, rough picture first when a change is expected to take long (Android's quick preview).
    func quick(_ params: MandalaParameters, hot: Bool = false, ticket: RenderTicket) -> Rendered? {
        if ticket.isCancelled() { return nil }
        return bridge.renderQuick(params: params, threads: previewThreads(hot: hot), ticket: ticket, imageLoader: loader, textRenderer: text)
    }

    /// The square picture as saved; `progress` gets whole percents on the rendering thread.
    func square(_ params: MandalaParameters, size: Int, ticket: RenderTicket, progress: @escaping @Sendable (Int) -> Void) -> Rendered? {
        bridge.renderSquare(params: params, size: Int32(size), threads: threads, ticket: ticket, imageLoader: loader, textRenderer: text) { percent in
            progress(Int(percent.int32Value))
        }
    }

    /// After a memory warning: the kept layer images go first.
    func dropCache() {
        cache.clear()
        text.dropFonts()
        bridge.collect()
    }
}

extension Rendered {
    /// The picture as a CGImage (sRGB, no alpha), sharing the bytes Kotlin made.
    var cgImage: CGImage? {
        let data = rgba as Data
        guard let provider = CGDataProvider(data: data as CFData) else { return nil }
        return CGImage(
            width: Int(width), height: Int(height), bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: Int(width) * 4,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }
}
