import SwiftUI
import AppKit

struct MandalaGeneratorApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .frame(minWidth: 1000, minHeight: 700)
        }
        .commands {
            CommandGroup(replacing: .saveItem) {
                Button("Save") {
                    appState.saveImage()
                }
                .keyboardShortcut("s", modifiers: .command)

                Button("Export Batch…") {
                    appState.exportBatch(count: 9)
                }
                .keyboardShortcut("e", modifiers: [.command, .shift])
            }

            CommandMenu("Image") {
                Button("Generate") {
                    Task { await appState.generate() }
                }
                .keyboardShortcut("r", modifiers: .command)

                Button("Randomize Seed") {
                    appState.randomizeSeed()
                }
                .keyboardShortcut("r", modifiers: [.command, .shift])
            }
        }
    }
}
