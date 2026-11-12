import SwiftUI
import UIKit

@main
struct DahliaApp: App {
    // The tour first: it asks whether a saved mandala existed before the editor starts saving.
    @State private var tour = TourModel()
    @State private var model = EditorModel()
    /// Dark by default, light by choice, not following the system (as on Android). The choice comes with
    /// the menu (WP-7); until then a debug launch argument sets it: `-lightTheme YES`.
    @AppStorage("lightTheme") private var lightTheme = false

    var body: some Scene {
        WindowGroup {
            EditorView()
                .environment(model)
                .environment(tour)
                .environment(\.tokens, lightTheme ? .light : .dark)
                .preferredColorScheme(lightTheme ? .light : .dark)
                .tint(lightTheme ? Tokens.light.accent : Tokens.dark.accent)
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didReceiveMemoryWarningNotification)) { _ in
                    model.memoryWarning()
                }
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in ReviewPrompt.opened() }
                #if DEBUG
                .task {
                    DragProbe.openFileIfAsked(model)
                    await DragProbe.runIfAsked(model)
                }
                .task { FontMap.listIfAsked() }
                .task { ShotSetup.runIfAsked(model) }
                .task { ReviewPrompt.applyLaunchArguments() }
                #endif
        }
    }
}
