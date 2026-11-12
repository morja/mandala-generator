import SwiftUI
import UIKit

@main
struct DahliaApp: App {
    @State private var model = EditorModel()
    /// Dark by default, light by choice, not following the system (as on Android). The choice comes with
    /// the menu (WP-7); until then a debug launch argument sets it: `-lightTheme YES`.
    @AppStorage("lightTheme") private var lightTheme = false

    var body: some Scene {
        WindowGroup {
            EditorView()
                .environment(model)
                .environment(\.tokens, lightTheme ? .light : .dark)
                .preferredColorScheme(lightTheme ? .light : .dark)
                .tint(lightTheme ? Tokens.light.accent : Tokens.dark.accent)
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didReceiveMemoryWarningNotification)) { _ in
                    model.memoryWarning()
                }
        }
    }
}
