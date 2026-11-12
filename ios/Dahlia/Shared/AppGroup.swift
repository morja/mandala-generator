import Foundation

/// The App Group shared by the app and the widget extension (PLAN-4 section 3.3, approval A2): `group.<bundle id>`,
/// so the debug build and the store build never share pictures or flags. The identifier comes from the Info.plist
/// (`DahliaAppGroup`, set from the xcconfig). The one seam for the capability: while a build carries no App Group
/// entitlement (device builds until the App IDs have the group, IOS-9), `container` and `defaults` are nil and
/// nothing is written.
enum AppGroup {
    static let identifier = Bundle.main.object(forInfoDictionaryKey: "DahliaAppGroup") as? String ?? "group.one.tearoom.mandala"

    /// Pro is unlocked (purchased, or a debug build's test Pro); written by the app for the widget (F7 a).
    static let proUnlockedKey = "proUnlocked"

    static var container: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    static var defaults: UserDefaults? {
        guard container != nil else { return nil }
        return UserDefaults(suiteName: identifier)
    }
}
