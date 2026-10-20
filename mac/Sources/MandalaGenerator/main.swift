import AppKit
import SwiftUI

// Show tooltips (the .help texts on the many small icon buttons) after 0.2 s instead of
// AppKit's default of about a second.
UserDefaults.standard.register(defaults: ["NSInitialToolTipDelay": 200])

// Set activation policy before SwiftUI takes over — this must happen
// before App.main() is called when running as a plain binary (not .app bundle).
NSApplication.shared.setActivationPolicy(.regular)
NSApplication.shared.activate(ignoringOtherApps: true)

MandalaGeneratorApp.main()
