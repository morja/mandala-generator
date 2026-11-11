import CoreGraphics
import Foundation
import ImageIO
import MandalaCore
import UniformTypeIdentifiers

/// Renders the three cases at three sizes with the Kotlin core and logs the times (lines start with SPIKE3).
@MainActor
final class Bench: ObservableObject {
    @Published var lines: [String] = []
    @Published var image: CGImage?
    @Published var status = "Idle"
    @Published var running = false

    struct Size: Sendable {
        let name: String
        let size: Int32
        let supersample: Bool
        let glow: Int32
    }

    /// Same as SpikeBench.kt on the JVM: the Android editor's preview, the free and the Pro export.
    nonisolated static let sizes = [
        Size(name: "preview512", size: 512, supersample: true, glow: 800),
        Size(name: "export1024", size: 1024, supersample: true, glow: 1600),
        Size(name: "pro2048", size: 2048, supersample: false, glow: 1600),
    ]
    nonisolated static let cases = ["light", "favourite", "worst"]
    nonisolated static let runs = 3

    func start(threads: Int?) {
        running = true
        lines = []
        let args = ProcessInfo.processInfo.arguments
        let fromArgs = args.firstIndex(of: "-threads").flatMap { i in i + 1 < args.count ? Int(args[i + 1]) : nil }
        let count = threads ?? fromArgs ?? Int(IosBridge.shared.processors())
        Task.detached(priority: .userInitiated) {
            await self.run(threads: count)
        }
    }

    nonisolated(unsafe) private var logged: [String] = []

    nonisolated private func log(_ line: String) async {
        print("SPIKE3 \(line)")
        logged.append(line)
        await MainActor.run { self.lines.append(line) }
    }

    nonisolated private func run(threads: Int) async {
        let bridge = IosBridge.shared
        #if DEBUG
        let build = "debug"
        #else
        let build = "release"
        #endif
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-gcTarget"), i + 1 < args.count, let mb = Int32(args[i + 1]) { bridge.tuneGc(targetHeapMb: mb) }
        let only = args.firstIndex(of: "-cases").flatMap { i in i + 1 < args.count ? args[i + 1].split(separator: ",").map(String.init) : nil }
        await log("build=\(build) threads=\(threads) cores=\(bridge.processors()) device=\(deviceModel())")
        await log("gc \(bridge.gcSettings())")
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        for name in Self.cases where only?.contains(name) ?? true {
            let url = Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "Bench")!
            let params = bridge.decode(json: try! String(contentsOf: url, encoding: .utf8))
            for size in Self.sizes {
                await MainActor.run { self.status = "\(name) \(size.name)" }
                bridge.collect()
                let base = Footprint.current()
                let sampler = Footprint.Sampler()
                sampler.start()
                var times: [Int64] = []
                var last: RenderResult?
                for i in 0...Self.runs {
                    let wall = Date()
                    let result = bridge.render(params: params, size: size.size, supersample: size.supersample,
                                               glowReferenceWidth: size.glow, threads: Int32(threads))
                    let ms = Int64(Date().timeIntervalSince(wall) * 1000)
                    if i > 0 { times.append(ms) } // the first run is a warm-up, as on the JVM
                    last = result
                }
                let peak = sampler.stop()
                times.sort()
                let mb = Double(peak &- base) / 1_048_576
                await log("\(name) \(size.name) median=\(times[Self.runs / 2]) min=\(times[0]) runs=\(times) peakFootprintMB=\(String(format: "%.0f", mb)) baseMB=\(base / 1_048_576)")
                if let result = last {
                    let rgba = bridge.rgba(image: result.image)
                    if let cg = makeImage(rgba, Int(result.image.width), Int(result.image.height)) {
                        await MainActor.run { self.image = cg }
                        if size.name == "export1024" { writePNG(cg, docs.appendingPathComponent("\(name)-1024-t\(threads).png")) }
                    }
                }
            }
        }
        await log("lifetimePeakFootprintMB=\(Footprint.lifetimePeak() / 1_048_576)")
        await log("done")
        try? logged.joined(separator: "\n").write(to: docs.appendingPathComponent("results-t\(threads).txt"), atomically: true, encoding: .utf8)
        if args.contains("-exit") { exit(0) }
        await MainActor.run {
            self.status = "Done"
            self.running = false
        }
    }
}

nonisolated func makeImage(_ data: Data, _ width: Int, _ height: Int) -> CGImage? {
    guard let provider = CGDataProvider(data: data as CFData) else { return nil }
    return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                   space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                   provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
}

nonisolated func writePNG(_ image: CGImage, _ url: URL) {
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return }
    CGImageDestinationAddImage(dest, image, nil)
    CGImageDestinationFinalize(dest)
}

nonisolated func deviceModel() -> String {
    var info = utsname()
    uname(&info)
    return withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
}
