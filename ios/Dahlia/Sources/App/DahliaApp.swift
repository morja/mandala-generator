import SwiftUI
import UIKit

@main
struct DahliaApp: App {
    // The tour first: it asks whether a saved mandala existed before the editor starts saving.
    @State private var tour = TourModel()
    @State private var model = EditorModel()
    /// Dark by default, light by choice, not following the system (as on Android). The choice comes with
    /// the menu (WP-7); until then a debug launch argument sets it: `-lightTheme YES`.
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("lightTheme") private var lightTheme = false

    init() {
        // Before any screen: StoreKit's transaction updates are heard from launch on (an approved Ask to Buy, a refund).
        // Not while the app only hosts the StoreKit tests: its listener would take and finish the transactions the
        // tests' own stores wait for, so refunds and purchases from elsewhere reached them only now and then (IOS-13).
        if !Self.hostingTests { ProStore.shared.start() }
    }

    /// The app runs as the host of a test bundle.
    static var hostingTests: Bool { ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil }

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
                .onChange(of: scenePhase, initial: true) { _, phase in if phase == .active { ReviewPrompt.opened() } }
                // Each return to the app asks StoreKit again, as Android asks Play on every resume (ADR-0022).
                .onChange(of: scenePhase) { _, phase in if phase == .active && !Self.hostingTests { Task { await model.proStore.resumed() } } }
                // The widgets (WP-10): a return may be a new day; the theme and Pro change what they show.
                // A launch by the Shortcuts action in the background left the preview and widgets for now (WP-11).
                .onChange(of: scenePhase, initial: true) { _, phase in if phase == .active { model.startIfDeferred() } }
                .onChange(of: scenePhase) { _, phase in if phase == .active { model.widgetsChanged() } }
                .onChange(of: lightTheme) { model.widgetsChanged() }
                .onChange(of: model.proOwned) { model.widgetsChanged() }
                // A tap on a widget opens the app as it was and offers the widget's mandala when it differs.
                .onOpenURL { url in model.openedFromWidget(url) }
                #if DEBUG
                .task {
                    DragProbe.openFileIfAsked(model)
                    await DragProbe.runIfAsked(model)
                }
                .task { FontMap.listIfAsked() }
                .task { ShotSetup.runIfAsked(model) }
                .task { await ShotSetup.exportProbeIfAsked(model) }
                .task { ReviewPrompt.applyLaunchArguments() }
                .task { await WallpaperProbe.runIfAsked() }
                #endif
        }
        // The background app refresh renders the next widget pictures (approval A10); iOS ends it by cancelling.
        .backgroundTask(.appRefresh(WidgetSync.refreshID)) { [model] in await model.refreshWidgetsInBackground() }
    }
}
