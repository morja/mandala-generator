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
        // Styles: up to 2 layers, abbreviated to 4 chars each
        let styleAbbr = parameters.layers.prefix(2)
            .map { String($0.style.displayName.prefix(4)).lowercased() }
            .joined(separator: "+")
        // Palette index, symmetry (omit if 1)
        let pal = "p\(parameters.paletteIndex)"
        let sym = parameters.symmetry > 1 ? "x\(parameters.symmetry)" : ""
        // Stable hash over all parameters so any slider change gives a new filename
        var h = parameters.seed
        let mix: (UInt64) -> Void = { v in h = h &* 6364136223846793005 &+ v &+ 1 }
        mix(UInt64(parameters.paletteIndex))
        mix(UInt64(parameters.symmetry))
        mix(UInt64(parameters.outputSize))
        mix(UInt64(Int(parameters.density     * 1000)))
        mix(UInt64(Int(parameters.complexity  * 1000)))
        mix(UInt64(Int(parameters.glowIntensity * 1000)))
        mix(UInt64(Int(parameters.colorDrift  * 1000)))
        mix(UInt64(Int(parameters.ripple      * 1000)))
        mix(UInt64(Int(parameters.wash        * 1000)))
        mix(UInt64(Int(parameters.abstractLevel * 1000)))
        mix(UInt64(Int(parameters.saturation  * 1000)))
        mix(UInt64(Int(parameters.brightness  * 1000)))
        for layer in parameters.layers {
            mix(UInt64(bitPattern: Int64(layer.style.rawValue.hashValue)))
            mix(UInt64(Int(layer.scale       * 1000)))
            mix(UInt64(Int(layer.colorOffset * 1000)))
        }
        let hash = String(format: "%08x", h & 0xFFFFFFFF)
        return (["mandala", styleAbbr, pal, sym, hash]
            .filter { !$0.isEmpty }
            .joined(separator: "-"))
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
