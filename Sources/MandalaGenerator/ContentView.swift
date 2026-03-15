import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        HSplitView {
            CanvasView()
                .frame(minWidth: 400)

            PalettePanel()
                .frame(width: 280)
                .frame(minWidth: 280, maxWidth: 280)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 700, minHeight: 600)
    }
}
