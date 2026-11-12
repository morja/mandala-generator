import SwiftUI
@preconcurrency import MandalaCore

/// The tabs under the picture: a fixed row of five equal text tabs with an amber line under the open one,
/// as Android's PrimaryTabRow (F13 a), no sideways scrolling. Layers and Scene are built; Emblem, Text and
/// Draw (WP-6) show muted and cannot be opened yet, so the row already has its final shape.
struct ControlTabs: View {
    enum Tab: CaseIterable {
        case layers, scene, emblem, text, draw

        var isBuilt: Bool { self == .layers || self == .scene }
    }

    @State private var tab = Tab.layers
    /// Full opacity: the open tab shows, the other stays alive but unseen.
    private static let shown = 1.0
    /// Tabs that are not built yet.
    private static let dimmed = 0.45
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Tab.allCases, id: \.self) { option in
                    Button {
                        tab = option
                    } label: {
                        Text(label(option)).textRole(.heading).lineLimit(1).minimumScaleFactor(0.7)
                            .foregroundStyle(option == tab ? t.accent : t.textMuted)
                            .padding(.vertical, Spacing.md)
                            .frame(maxWidth: .infinity)
                            .overlay(alignment: .bottom) {
                                if option == tab { Rectangle().fill(t.accent).frame(height: Sizes.progress) }
                            }
                    }
                    .buttonStyle(.plain)
                    .disabled(!option.isBuilt)
                    .opacity(option.isBuilt ? Self.shown : Self.dimmed)
                    .accessibilityAddTraits(option == tab ? [.isSelected, .isHeader] : .isHeader)
                }
            }
            Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
            // Both built tabs stay alive, so each keeps its scroll position and open groups while the other shows.
            ZStack {
                tabView(.layers) { LayersTab() }
                tabView(.scene) { SceneTab() }
            }
        }
    }

    private func tabView<Content: View>(_ option: Tab, @ViewBuilder content: () -> Content) -> some View {
        ScrollView { content() }
            .scrollDismissesKeyboard(.immediately)
            .opacity(tab == option ? Self.shown : 0)
            .allowsHitTesting(tab == option)
            .accessibilityHidden(tab != option)
    }

    private func label(_ tab: Tab) -> String {
        switch tab {
        case .layers: Texts.layers
        case .scene: Texts.tabScene
        case .emblem: Texts.tabEmblem
        case .text: Texts.tabText
        case .draw: Texts.tabDraw
        }
    }
}
