import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        HSplitView {
            ScenePanel()
                .frame(width: 286)
                .frame(minWidth: 286, maxWidth: 286)

            CanvasView()
                .frame(minWidth: 380)

            PalettePanel()
                .frame(width: 360)
                .frame(minWidth: 360, maxWidth: 360)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 1100, minHeight: 600)
    }
}
