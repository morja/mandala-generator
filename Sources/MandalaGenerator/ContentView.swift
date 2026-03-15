import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        HSplitView {
            ScenePanel()
                .frame(width: 260)
                .frame(minWidth: 260, maxWidth: 260)

            CanvasView()
                .frame(minWidth: 380)

            PalettePanel()
                .frame(width: 280)
                .frame(minWidth: 280, maxWidth: 280)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 940, minHeight: 600)
    }
}
