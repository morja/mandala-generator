import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        HSplitView {
            ParameterPanel()
                .frame(width: 260)
                .frame(minWidth: 260, maxWidth: 260)

            CanvasView()
                .frame(minWidth: 400)

            PalettePanel()
                .frame(width: 220)
                .frame(minWidth: 220, maxWidth: 220)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 1100, minHeight: 700)
    }
}
