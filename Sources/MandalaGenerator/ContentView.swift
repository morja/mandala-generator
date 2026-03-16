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
                .frame(width: 320)
                .frame(minWidth: 320, maxWidth: 320)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 980, minHeight: 600)
    }
}
