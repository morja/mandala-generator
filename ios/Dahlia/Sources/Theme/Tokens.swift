import SwiftUI

// The only place for colours, spacing, radii, sizes and text roles in the iPhone app (PLAN-4 section 3.10).
// TEMPORARY (WP-4): copied by hand from android/app/.../ui/theme/Tokens.kt (dp become points one to one);
// WP-4 generates this file from Tokens.kt and checks it. Screens use only these names.

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
}

enum Radius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
}

enum Sizes {
    static let swatch: CGFloat = 36
    static let swatchSmall: CGFloat = 24
    static let icon: CGFloat = 24
    static let favourite: CGFloat = 96
    static let swatchRing: CGFloat = 2
    static let progress: CGFloat = 3

    /** Room below the canvas for its status line. */
    static let statusLine: CGFloat = 24
}

/// One set of semantic colours; light and dark differ only here (ADR-0006).
struct Tokens: Sendable {
    let background: Color
    let surface: Color
    let surfaceRaised: Color
    let text: Color
    let textMuted: Color
    let outline: Color
    let accent: Color
    let onAccent: Color
    let accentSoft: Color
    let error: Color
    /// Behind the rendered image; always dark, because the mandala is drawn as light on black.
    let canvas: Color

    /// The other theme's accent, for the action on an inverted message (Android's `inversePrimary`).
    var inverseAccent: Color { self.accent == Tokens.dark.accent ? Tokens.light.accent : Tokens.dark.accent }

    static let dark = Tokens(
        background: Color(hex: 0x0A0908),
        surface: Color(hex: 0x121110),
        surfaceRaised: Color(hex: 0x1B1917),
        text: Color(hex: 0xE6E1DB),
        textMuted: Color(hex: 0x9C948B),
        outline: Color(hex: 0x2E2A26),
        accent: Color(hex: 0xD9A15C),
        onAccent: Color(hex: 0x241605),
        accentSoft: Color(hex: 0x2A2117),
        error: Color(hex: 0xF2A39A),
        canvas: Color(hex: 0x000000)
    )

    static let light = Tokens(
        background: Color(hex: 0xF7F4EF),
        surface: Color(hex: 0xFFFDFA),
        surfaceRaised: Color(hex: 0xEFEAE3),
        text: Color(hex: 0x221F1C),
        textMuted: Color(hex: 0x5F5851),
        outline: Color(hex: 0xCFC7BD),
        accent: Color(hex: 0x8A5210),
        onAccent: Color(hex: 0xFFFFFF),
        accentSoft: Color(hex: 0xF3E3CF),
        error: Color(hex: 0xB3261E),
        canvas: Color(hex: 0x000000)
    )
}

/// Text roles on Dynamic Type (approval A9). TEMPORARY (WP-4): the three roles WP-1 needs, mirroring the
/// Android type scale it uses (titleMedium, titleSmall, bodyMedium, bodySmall); WP-4 lists the final six.
enum TextRole {
    /// Sheet titles (Android titleMedium).
    case title
    /// Group headings and tabs (Android titleSmall): the body size, heavier.
    case heading
    /// Labels and rows (Android bodyMedium).
    case body
    /// Summaries, values and hints, shown muted (Android bodySmall).
    case note

    var font: Font {
        switch self {
        case .title: .title3.weight(.semibold)
        case .heading: .subheadline.weight(.semibold)
        case .body: .subheadline
        case .note: .caption
        }
    }
}

extension Color {
    /// sRGB from 0xRRGGBB; used only in this file.
    fileprivate init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

private struct TokensKey: EnvironmentKey {
    static let defaultValue = Tokens.dark
}

extension EnvironmentValues {
    var tokens: Tokens {
        get { self[TokensKey.self] }
        set { self[TokensKey.self] = newValue }
    }
}

extension View {
    func textRole(_ role: TextRole) -> some View { font(role.font) }
}
