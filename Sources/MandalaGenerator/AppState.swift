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
        isGenerating = true
        let params = parameters
        let start = Date()
        let image = await Task.detached(priority: .userInitiated) {
            MandalaRenderer.render(params: params)
        }.value
        let elapsed = Date().timeIntervalSince(start)
        currentImage = image
        lastGenerationTime = elapsed
        isGenerating = false
    }

    func randomizeSeed() {
        parameters.seed = UInt64.random(in: 1...UInt64.max)
    }

    func saveImage() {
        guard let image = currentImage else { return }
        let panel = NSSavePanel()
        panel.title = "Save Mandala"
        panel.allowedContentTypes = [.png, .jpeg]
        panel.nameFieldStringValue = "mandala-\(parameters.seed)"
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
                for i in 0..<count {
                    var p = baseParams
                    p.seed = UInt64.random(in: 1...UInt64.max)
                    let image = await Task.detached(priority: .userInitiated) {
                        MandalaRenderer.render(params: p)
                    }.value
                    let fileURL = url.appendingPathComponent("mandala-\(i+1)-\(p.seed).png")
                    self.writeImage(image, to: fileURL)
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
