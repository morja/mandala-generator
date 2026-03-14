import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        HSplitView {
            ParameterPanel()
                .frame(width: 300)
                .frame(minWidth: 300, maxWidth: 300)

            CanvasView()
                .frame(minWidth: 400)
        }
        .background(Color(NSColor.windowBackgroundColor))
        .frame(minWidth: 1000, minHeight: 700)
    }
}
