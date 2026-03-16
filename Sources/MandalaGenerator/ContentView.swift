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
                .frame(width: 360)
                .frame(minWidth: 360, maxWidth: 360)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 1020, minHeight: 600)
    }
}
