import CoreText
import Foundation
@preconcurrency import MandalaCore

#if DEBUG
extension FontMap {
    /// Debug builds list the installed families and what each font of the text tab maps to, for the font
    /// table in IOS-5 (PLAN-4 section 7): launch with `-listFonts YES`.
    static func listIfAsked() {
        guard UserDefaults.standard.bool(forKey: "listFonts") else { return }
        let families = CTFontManagerCopyAvailableFontFamilyNames() as? [String] ?? []
        Engine.trace("fonts families=\(families.sorted().joined(separator: "|"))")
        for name in IosEditingParts.shared.fontNames() {
            let font = TextFont(name: name, sizePx: 12, italic: false, trackingPx: 0)
            let used = installedName(for: font)
            let ct = CTFontCreateWithName(used as CFString, 12, nil)
            let italic = CTFontCreateCopyWithSymbolicTraits(ct, 0, nil, .traitItalic, .traitItalic).map { CTFontCopyPostScriptName($0) as String } ?? "oblique"
            Engine.trace("fonts map \(name) -> \(used) (\(CTFontCopyPostScriptName(ct) as String), own=\(isInstalled(name)), italic=\(italic))")
        }
    }
}
#endif
