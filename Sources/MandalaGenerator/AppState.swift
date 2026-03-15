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

    private var debounceTask: Task<Void, Never>? = nil
    private var parameterCancellable: AnyCancellable?
    private var lastRenderedParams: MandalaParameters? = nil

    init() {
        // Debounced auto-generate when parameters change
        parameterCancellable = $parameters
            .dropFirst()
            .sink { [weak self] _ in
                guard let self else { return }
                self.debounceTask?.cancel()
                self.debounceTask = Task {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    guard !Task.isCancelled else { return }
                    await self.generate()
                }
            }
    }

    func generate() async {
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
    }

    private func suggestedFilename() -> String {
        let styleAbbr = parameters.layers.prefix(2)
            .map { String($0.style.displayName.prefix(4)).lowercased() }
            .joined(separator: "+")
        let pal = "p\(parameters.layers.first?.paletteIndex ?? 0)"
        let sym = parameters.symmetry > 1 ? "x\(parameters.symmetry)" : ""
        var h = parameters.seed
        let mix: (UInt64) -> Void = { v in h = h &* 6364136223846793005 &+ v &+ 1 }
        mix(UInt64(parameters.symmetry))
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
            mix(UInt64(layer.paletteIndex))
        }
        let hash = String(format: "%08x", h & 0xFFFFFFFF)
        return (["mandala", styleAbbr, pal, sym, hash].filter { !$0.isEmpty }.joined(separator: "-"))
    }

    func randomizeAll() {
        randomizeSeed()
        parameters.symmetry = Int.random(in: 1...8)

        let allStyles = MandalaStyle.allCases
        let nLayers = Int.random(in: 1...3)
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
                brightness: Double.random(in: 0.3...0.7)
            ))
        }
        parameters.layers = newLayers
        Task { await generate() }
    }

    func randomizeSeed() {
        parameters.seed = UInt64.random(in: 1...UInt64.max)
    }

    func saveImage() {
        guard let image = currentImage else { return }
        let panel = NSSavePanel()
        panel.title = "Save Mandala"
        panel.allowedContentTypes = [.png, .jpeg]
        panel.nameFieldStringValue = suggestedFilename()
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
        guard let tiffData = image.tiffRepresentation,
              let bitmap = NSBitmapImageRep(data: tiffData) else { return }

        let ext = url.pathExtension.lowercased()
        let data: Data?
        if ext == "jpg" || ext == "jpeg" {
            data = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.92])
        } else {
            data = bitmap.representation(using: .png, properties: [:])
        }
        try? data?.write(to: url)
    }
}
