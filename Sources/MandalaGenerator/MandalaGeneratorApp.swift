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
                Button("Save Settings…") {
                    appState.saveSettings()
                }
                .keyboardShortcut("s", modifiers: .command)

                Button("Load Settings…") {
                    appState.loadSettings()
                }
                .keyboardShortcut("o", modifiers: .command)

                Divider()

                Button("Save Image…") {
                    appState.saveImage()
                }
                .keyboardShortcut("s", modifiers: [.command, .shift])

                Button("Export Batch…") {
                    appState.exportBatch(count: 9)
                }
                .keyboardShortcut("e", modifiers: [.command, .shift])

                Button("Export Animation…") {
                    appState.showAnimationOptions = true
                }
                .keyboardShortcut("e", modifiers: [.command, .option])
            }

            CommandGroup(after: .pasteboard) {
                Divider()
                Button("Copy Image") {
                    appState.copyToClipboard()
                }
                .keyboardShortcut("c", modifiers: [.command, .shift])
            }

            CommandMenu("Image") {
                Button("Generate") {
                    Task { await appState.generate() }
                }
                .keyboardShortcut("r", modifiers: .command)

                Button("Randomize All") {
                    appState.randomizeAll()
                }
                .keyboardShortcut("r", modifiers: [.command, .shift])

                Button("Randomize Seed") {
                    appState.randomizeSeed()
                }
            }
        }
    }
}
