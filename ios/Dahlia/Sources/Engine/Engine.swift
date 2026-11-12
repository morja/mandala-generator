import CoreGraphics
import Foundation
@preconcurrency import MandalaCore
import os

// Kotlin data classes are immutable; the ticket is atomic; a rendered picture is never changed after it is made.
extension MandalaParameters: @retroactive @unchecked Sendable {}
extension Favorite: @retroactive @unchecked Sendable {}
extension Rendered: @retroactive @unchecked Sendable {}
extension RenderTicket: @retroactive @unchecked Sendable {}

/// Owns every render call (PLAN-4 section 3.2). Kotlin calls block, so the actor runs on its own serial
/// queue, never on the main thread or Swift's cooperative pool; inside a render core's worker pool
/// (`Parallel`) uses all cores. The layer cache of the editor's preview lives here and nowhere else.
actor Engine {
    private let queue = DispatchSerialQueue(label: "one.tearoom.mandala.engine", qos: .userInitiated)
    nonisolated var unownedExecutor: UnownedSerialExecutor { queue.asUnownedSerialExecutor() }

    private let bridge = IosBridge.shared
    private let cache: LayerCache
    private let threads: Int32

    static let log = Logger(subsystem: "one.tearoom.mandala", category: "engine")

    /// The collector target the app starts with (PLAN-4 approval A5): 0 keeps Kotlin/Native's
    /// autotuned collector. Set to 64 only if the iPhone 15 measurement confirms the gain (DEV-1).
    static let collectorTargetMB = 0

    init() {
        cache = IosBridge.shared.makeLayerCache()
        threads = IosBridge.shared.processors()
        // Debug builds may try another target: launch with `-collectorTargetMB 64`.
        #if DEBUG
        let target = UserDefaults.standard.object(forKey: "collectorTargetMB") as? Int ?? Self.collectorTargetMB
        #else
        let target = Self.collectorTargetMB
        #endif
        IosBridge.shared.tuneGc(targetHeapMb: Int32(target))
        Self.log.info("DAHLIA engine threads=\(IosBridge.shared.processors()) gc \(IosBridge.shared.gcSettings(), privacy: .public)")
    }

    /// The editor's 512 px preview with the layer cache; nil when cancelled.
    func preview(_ params: MandalaParameters, fast: Bool, ticket: RenderTicket) -> Rendered? {
        if ticket.isCancelled() { return nil }
        return bridge.renderPreview(params: params, cache: cache, fast: fast, threads: threads, ticket: ticket)
    }

    /// A small, rough picture first when a change is expected to take long (Android's quick preview).
    func quick(_ params: MandalaParameters, ticket: RenderTicket) -> Rendered? {
        if ticket.isCancelled() { return nil }
        return bridge.renderQuick(params: params, threads: threads, ticket: ticket)
    }

    /// The square picture as saved; `progress` gets whole percents on the rendering thread.
    func square(_ params: MandalaParameters, size: Int, ticket: RenderTicket, progress: @escaping @Sendable (Int) -> Void) -> Rendered? {
        bridge.renderSquare(params: params, size: Int32(size), threads: threads, ticket: ticket) { percent in
            progress(Int(percent.int32Value))
        }
    }

    /// After a memory warning: the kept layer images go first.
    func dropCache() {
        cache.clear()
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
