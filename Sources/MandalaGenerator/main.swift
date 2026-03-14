import AppKit
import SwiftUI

// Set activation policy before SwiftUI takes over — this must happen
// before App.main() is called when running as a plain binary (not .app bundle).
NSApplication.shared.setActivationPolicy(.regular)
NSApplication.shared.activate(ignoringOtherApps: true)

MandalaGeneratorApp.main()
