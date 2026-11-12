import SwiftUI

/// About (Android's `AboutScreen`): name and version, the licence with the section 7 permission, the privacy
/// policy and what the iPhone app is built with. Same texts as Android; the library list is the iOS one.
struct AboutView: View {
    let close: @MainActor () -> Void
    @Environment(\.tokens) private var t

    private static let sourceURL = "https://github.com/morja/mandala-generator"
    private static let gplURL = "https://www.gnu.org/licenses/gpl-3.0.html"
    private static let apacheURL = "https://www.apache.org/licenses/LICENSE-2.0"

    /// Same wording as the root README and ADR-0008.
    private static let section7 =
        "If you modify this Program, or any covered work, by linking or combining it with the Google Play Billing Library, "
        + "the Google Play Core libraries (such as Google Play In-App Review) or with the system and platform libraries of Android, macOS or iOS (or modified versions of those libraries), "
        + "containing parts covered by the terms of their own licences, the licensors of this Program grant you additional "
        + "permission to convey the resulting work. You may also convey the Program through app stores (such as Google Play "
        + "and the Apple App Store) whose terms of service add restrictions on use or copying."

    /// What the iPhone app is built with: the Kotlin runtime and kotlinx.serialization inside MandalaCore. SwiftUI,
    /// StoreKit and the other system frameworks belong to iOS and are covered by the section 7 permission.
    private static let libraries = [
        ("Kotlin standard library and Kotlin/Native runtime", "Apache License 2.0"),
        ("kotlinx.serialization", "Apache License 2.0"),
    ]

    private var version: String {
        let info = Bundle.main.infoDictionary
        return Texts.aboutVersion(info?["CFBundleShortVersionString"] as? String ?? "", Int(info?["CFBundleVersion"] as? String ?? "") ?? 0)
    }

    private var privacyURL: String {
        let german = Locale.preferredLanguages.first?.hasPrefix("de") ?? false
        return "https://www.tearoom.one/\(german ? "de" : "en")/apps/privacy/mandala-generator"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                header
                section(Texts.aboutLicence) {
                    paragraph(verbatim: "Copyright © 2026 Mathis Koblin")
                    paragraph(Texts.aboutFreeSoftware)
                    link(Texts.aboutSourceCode, Self.sourceURL)
                    link(verbatim: "GNU General Public License 3.0", Self.gplURL)
                    ControlGroup(Texts.aboutAdditionalPermission, summary: Texts.aboutGplSection7) {
                        Text(verbatim: Self.section7).textRole(.note).foregroundStyle(t.textMuted)
                    }
                }
                section(Texts.aboutPrivacy) {
                    paragraph(Texts.aboutPrivacyText)
                    link(Texts.aboutPrivacyPolicy, privacyURL)
                }
                section(Texts.aboutLibraries) {
                    ForEach(Self.libraries, id: \.0) { library in
                        VStack(alignment: .leading, spacing: 0) {
                            Text(verbatim: library.0).textRole(.body).foregroundStyle(t.text)
                            link(verbatim: library.1, Self.apacheURL, role: .note)
                        }
                    }
                    ControlGroup("Apache License 2.0", summary: Texts.aboutFullText) {
                        ForEach(Array(Self.apacheParagraphs.enumerated()), id: \.offset) { _, p in
                            Text(verbatim: p).textRole(.note).foregroundStyle(t.textMuted)
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xl)
        }
        .overlay(alignment: .topTrailing) {
            Button(action: close) {
                Image(systemName: "xmark").foregroundStyle(t.textMuted).frame(width: Sizes.icon * 2, height: Sizes.icon * 2).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(Spacing.sm)
            .accessibilityLabel(Texts.aboutClose)
        }
        .background(t.background)
        .presentationBackground(t.background)
        .presentationDetents([.large])
    }

    private var header: some View {
        VStack(spacing: Spacing.xs) {
            Image("AboutArt").resizable().scaledToFit().frame(width: Sizes.favourite, height: Sizes.favourite).clipShape(Circle())
                .accessibilityHidden(true)
            Text(verbatim: "Dahlia – Mandala Maker").textRole(.title).foregroundStyle(t.text)
            Text(version).textRole(.body).foregroundStyle(t.textMuted)
        }
        .frame(maxWidth: .infinity)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(title).textRole(.title).foregroundStyle(t.text)
            content()
        }
    }

    private func paragraph(_ text: String) -> some View {
        Text(text).textRole(.body).foregroundStyle(t.text).fixedSize(horizontal: false, vertical: true)
    }

    private func paragraph(verbatim text: String) -> some View {
        Text(verbatim: text).textRole(.body).foregroundStyle(t.text)
    }

    /// A text link in the accent colour; opens the browser.
    private func link(_ text: String, _ url: String, role: TextRole = .body) -> some View {
        Link(destination: URL(string: url)!) { Text(text).textRole(role).foregroundStyle(t.accent).underline().multilineTextAlignment(.leading) }
    }

    private func link(verbatim text: String, _ url: String, role: TextRole = .body) -> some View {
        Link(destination: URL(string: url)!) { Text(verbatim: text).textRole(role).foregroundStyle(t.accent).underline().multilineTextAlignment(.leading) }
    }

    /// The bundled licence text, with its hard line breaks joined so it wraps to the screen.
    private static let apacheParagraphs: [String] = {
        guard let url = Bundle.main.url(forResource: "apache-2.0", withExtension: "txt"), let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.components(separatedBy: "\n\n")
            .map { $0.split(separator: "\n", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: " ").trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }()
}
