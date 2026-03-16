import AppKit
import Combine
import Foundation
import SwiftUI

@MainActor
class AppState: ObservableObject {
    @Published var parameters = MandalaParameters()
    @Published var currentImage: NSImage? = nil
    @Published var isGenerating = false
    @Published var lastGenerationTime: Double = 0
    @Published var canGoBack: Bool = false
    @Published var canGoForward: Bool = false

    private var debounceTask: Task<Void, Never>? = nil
    private var parameterCancellable: AnyCancellable?
    private var lastRenderedParams: MandalaParameters? = nil
    private var history: [MandalaParameters] = []
    private var historyIndex: Int = -1
    private var isNavigatingHistory: Bool = false

    init() {
        // Debounced auto-generate when parameters change
        parameterCancellable = $parameters
            .dropFirst()
            .sink { [weak self] _ in
                guard let self, !self.isNavigatingHistory else { return }
                self.debounceTask?.cancel()
                self.debounceTask = Task {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    guard !Task.isCancelled else { return }
                    await self.generate()
                }
            }

        // Start with a random mandala or restore history
        let hasHistory = loadHistory()
        if hasHistory {
            isNavigatingHistory = true
            Task { await generate() }
        } else {
            randomizeAll()
        }
    }

    func generate() async {
        let navigating = isNavigatingHistory
        defer { isNavigatingHistory = false }
        guard !isGenerating else { return }
        guard parameters != lastRenderedParams else { return }
        debounceTask?.cancel()
        debounceTask = nil
        isGenerating = true
        let params = parameters
        lastRenderedParams = params
        let start = Date()
        let image = await Task.detached(priority: .userInitiated) {
            MandalaRenderer.render(params: params)
        }.value
        let elapsed = Date().timeIntervalSince(start)
        currentImage = image
        lastGenerationTime = elapsed
        isGenerating = false
        if !navigating {
            pushHistory()
        }
    }

    private func suggestedFilename() -> String {
        let styleAbbr = parameters.layers.prefix(2)
            .map { String($0.style.displayName.prefix(4)).lowercased() }
            .joined(separator: "+")
        let pal = "p\(parameters.layers.first?.paletteIndex ?? 0)"
        let sym = (parameters.layers.first?.symmetry ?? 1) > 1 ? "x\(parameters.layers.first!.symmetry)" : ""
        var h = parameters.seed
        let mix: (UInt64) -> Void = { v in h = h &* 6364136223846793005 &+ v &+ 1 }
        mix(UInt64(parameters.outputSize))
        for layer in parameters.layers {
            mix(UInt64(bitPattern: Int64(layer.style.rawValue.hashValue)))
            mix(UInt64(Int(layer.scale         * 1000)))
            mix(UInt64(Int(layer.complexity    * 1000)))
            mix(UInt64(Int(layer.density       * 1000)))
            mix(UInt64(Int(layer.glowIntensity * 1000)))
            mix(UInt64(Int(layer.colorDrift    * 1000)))
            mix(UInt64(Int(layer.ripple        * 1000)))
            mix(UInt64(Int(layer.wash          * 1000)))
            mix(UInt64(Int(layer.abstractLevel * 1000)))
            mix(UInt64(Int(layer.saturation    * 1000)))
            mix(UInt64(Int(layer.brightness    * 1000)))
            mix(UInt64(layer.symmetry))
            mix(layer.seed)
            mix(UInt64(layer.paletteIndex))
            mix(layer.isEnabled ? 1 : 0)
        }
        // Background layer
        let bg = parameters.baseLayer
        if bg.isEnabled {
            mix(UInt64(bitPattern: Int64(bg.type.rawValue.hashValue)))
            mix(UInt64(Int(bg.hue        * 1000)))
            mix(UInt64(Int(bg.saturation * 1000)))
            mix(UInt64(Int(bg.brightness * 1000)))
            mix(UInt64(Int(bg.hue2       * 1000)))
            mix(UInt64(Int(bg.patternType)))
            mix(UInt64(Int(bg.patternScale   * 1000)))
            mix(UInt64(Int(bg.grainAmount    * 1000)))
            mix(UInt64(Int(bg.opacity        * 1000)))
            mix(bg.isRadial ? 1 : 0)
        }
        // Effects layer
        let fx = parameters.effectsLayer
        if fx.isEnabled {
            mix(UInt64(Int(fx.dimming     * 1000)))
            mix(UInt64(Int(fx.erasure     * 1000)))
            mix(UInt64(Int(fx.highlights  * 1000)))
            mix(UInt64(Int(fx.stars       * 1000)))
            mix(UInt64(Int(fx.vignette    * 1000)))
            mix(UInt64(Int(fx.chromatic   * 1000)))
            mix(UInt64(Int(fx.brightness  * 1000)))
            mix(UInt64(Int(fx.contrast    * 1000)))
            mix(UInt64(Int(fx.relief      * 1000)))
            mix(UInt64(Int(fx.reliefAngle * 1000)))
            mix(fx.dimmingSeed)
            mix(fx.erasureSeed)
            mix(fx.highlightsSeed)
            mix(fx.starsSeed)
        }
        let bgTag = bg.isEnabled  ? "bg-\(String(bg.type.rawValue.prefix(3)))" : ""
        let fxTag = fx.isEnabled  ? "fx" : ""
        let hash = String(format: "%08x", h & 0xFFFFFFFF)
        return (["mandala", styleAbbr, pal, sym, bgTag, fxTag, hash]
            .filter { !$0.isEmpty }.joined(separator: "-"))
    }

    func randomizeAll() {
        randomizeSeed()

        let allStyles = MandalaStyle.allCases
        let nLayers = Int.random(in: 1...3)
        let sharedSymmetry = Int.random(in: 1...8)
        var newLayers: [StyleLayer] = []
        var usedStyles = Set<String>()
        for li in 0..<nLayers {
            var s = allStyles.randomElement() ?? .mixed
            var tries = 0
            while usedStyles.contains(s.rawValue) && tries < 20 {
                s = allStyles.randomElement() ?? .mixed
                tries += 1
            }
            usedStyles.insert(s.rawValue)
            newLayers.append(StyleLayer(
                style: s,
                scale: li == 0 ? Double.random(in: 0.75...1.0) : Double.random(in: 0.3...0.75),
                paletteIndex: Int.random(in: 0..<ColorPalettes.all.count),
                colorOffset: Double.random(in: 0...1),
                complexity: Double.random(in: 0.2...1.0),
                density: Double.random(in: 0.2...1.0),
                glowIntensity: Double.random(in: 0.2...0.9),
                colorDrift: Double.random(in: 0.1...0.9),
                ripple: Double.random(in: 0.0...0.6),
                wash: Double.random(in: 0.0...0.5),
                abstractLevel: Double.random(in: 0.1...0.8),
                saturation: Double.random(in: 0.3...1.0),
                brightness: Double.random(in: 0.3...0.7),
                symmetry: sharedSymmetry,
                seed: UInt64.random(in: 1...UInt64.max)
            ))
        }
        parameters.layers = newLayers
        Task { await generate() }
    }

    func goBack() {
        guard historyIndex > 0 else { return }
        historyIndex -= 1
        isNavigatingHistory = true
        parameters = history[historyIndex]
        updateHistoryState()
        Task { await generate() }
    }

    func goForward() {
        guard historyIndex < history.count - 1 else { return }
        historyIndex += 1
        isNavigatingHistory = true
        parameters = history[historyIndex]
        updateHistoryState()
        Task { await generate() }
    }

    private func pushHistory() {
        if historyIndex < history.count - 1 {
            history = Array(history.prefix(historyIndex + 1))
        }
        history.append(parameters)
        if history.count > 50 { history.removeFirst() }
        historyIndex = history.count - 1
        updateHistoryState()
        saveHistory()
    }

    private func updateHistoryState() {
        canGoBack = historyIndex > 0
        canGoForward = historyIndex < history.count - 1
    }

    private var historyFileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("MandalaGenerator", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("history.json")
    }

    private func saveHistory() {
        guard let data = try? JSONEncoder().encode(history) else { return }
        try? data.write(to: historyFileURL)
    }

    @discardableResult
    private func loadHistory() -> Bool {
        guard let data = try? Data(contentsOf: historyFileURL),
              let loaded = try? JSONDecoder().decode([MandalaParameters].self, from: data),
              !loaded.isEmpty else { return false }
        history = loaded
        historyIndex = history.count - 1
        parameters = history[historyIndex]
        updateHistoryState()
        return true
    }

    func randomizeSeed() {
        parameters.seed = UInt64.random(in: 1...UInt64.max)
        for i in parameters.layers.indices {
            parameters.layers[i].seed = UInt64.random(in: 1...UInt64.max)
        }
    }

    func saveSettings() {
        let panel = NSSavePanel()
        panel.title = "Save Settings"
        panel.allowedContentTypes = [.json]
        panel.nameFieldStringValue = suggestedFilename() + ".json"
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url, let self else { return }
            guard let data = try? JSONEncoder().encode(self.parameters) else { return }
            try? data.write(to: url)
        }
    }

    func loadSettings() {
        let panel = NSOpenPanel()
        panel.title = "Load Settings"
        panel.allowedContentTypes = [.json]
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url, let self else { return }
            guard let data = try? Data(contentsOf: url),
                  let params = try? JSONDecoder().decode(MandalaParameters.self, from: data) else { return }
            Task { @MainActor [weak self] in
                self?.parameters = params
            }
        }
    }

    func saveImage() {
        guard let image = currentImage else { return }
        let panel = NSSavePanel()
        panel.title = "Save Mandala"
        panel.allowedContentTypes = parameters.outputFormat == "jpg" ? [.jpeg] : [.png]
        panel.nameFieldStringValue = suggestedFilename() + "." + parameters.outputFormat
        panel.begin { [weak self] response in
            guard response == .OK, let url = panel.url else { return }
            self?.writeImage(image, to: url)
        }
    }

    func exportBatch(count: Int) {
        let openPanel = NSOpenPanel()
        openPanel.title = "Choose Export Folder"
        openPanel.canChooseFiles = false
        openPanel.canChooseDirectories = true
        openPanel.canCreateDirectories = true
        openPanel.begin { [weak self] response in
            guard response == .OK, let url = openPanel.url, let self else { return }
            Task { [weak self] in
                guard let self else { return }
                let baseParams = self.parameters
                // Build all param variants up-front
                let variants: [(Int, MandalaParameters)] = (0..<count).map { i in
                    var p = baseParams
                    p.seed = UInt64.random(in: 1...UInt64.max)
                    return (i, p)
                }
                // Render all in parallel via TaskGroup
                await withTaskGroup(of: (Int, NSImage, UInt64).self) { group in
                    for (i, p) in variants {
                        group.addTask(priority: .userInitiated) {
                            let img = MandalaRenderer.render(params: p)
                            return (i, img, p.seed)
                        }
                    }
                    for await (i, image, seed) in group {
                        let fileURL = url.appendingPathComponent("mandala-\(i+1)-\(seed).png")
                        self.writeImage(image, to: fileURL)
                    }
                }
            }
        }
    }

    private func writeImage(_ image: NSImage, to url: URL) {
        let ext = url.pathExtension.lowercased()
        let isPNG = ext != "jpg" && ext != "jpeg"

        // Apply shape mask for PNG exports
        let finalImage = isPNG && parameters.outputShape != "square"
            ? maskedImage(image, shape: parameters.outputShape) ?? image
            : image

        guard let tiffData = finalImage.tiffRepresentation,
              let bitmap = NSBitmapImageRep(data: tiffData) else { return }

        let data: Data?
        if !isPNG {
            data = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.92])
        } else {
            data = bitmap.representation(using: .png, properties: [:])
        }
        try? data?.write(to: url)
    }

    /// Returns a copy of `image` clipped to a circle or squircle, with alpha transparency.
    private func maskedImage(_ image: NSImage, shape: String) -> NSImage? {
        let size = image.size
        let w = Int(size.width), h = Int(size.height)
        guard w > 0, h > 0 else { return nil }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: nil, width: w, height: h,
            bitsPerComponent: 8, bytesPerRow: w * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }

        let rect = CGRect(x: 0, y: 0, width: w, height: h)

        // Build clip path
        let path: CGPath
        switch shape {
        case "circle":
            path = CGPath(ellipseIn: rect, transform: nil)
        case "squircle":
            path = squirclePath(in: rect, exponent: 4.0)
        case "rounded":
            let r = min(rect.width, rect.height) * 0.08
            path = CGPath(roundedRect: rect, cornerWidth: r, cornerHeight: r, transform: nil)
        default:
            return image
        }

        ctx.addPath(path)
        ctx.clip()

        // Draw image into clipped context
        guard let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else { return nil }
        ctx.draw(cgImage, in: rect)

        guard let masked = ctx.makeImage() else { return nil }
        return NSImage(cgImage: masked, size: size)
    }

    /// Superellipse path: |x/rx|^n + |y/ry|^n = 1, approximated with line segments.
    private func squirclePath(in rect: CGRect, exponent: CGFloat) -> CGPath {
        let path = CGMutablePath()
        let cx = rect.midX, cy = rect.midY
        let rx = rect.width * 0.5, ry = rect.height * 0.5
        let steps = 512
        let inv = 2.0 / exponent
        for i in 0...steps {
            let t = CGFloat(i) / CGFloat(steps) * 2 * .pi
            let cosT = cos(t), sinT = sin(t)
            let x = cx + rx * (cosT < 0 ? -1 : 1) * pow(abs(cosT), inv)
            let y = cy + ry * (sinT < 0 ? -1 : 1) * pow(abs(sinT), inv)
            if i == 0 { path.move(to: CGPoint(x: x, y: y)) }
            else       { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        path.closeSubpath()
        return path
    }
}
