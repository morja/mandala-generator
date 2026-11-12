import SwiftUI
@preconcurrency import MandalaCore

/// The tabs under the picture: a fixed row of five equal text tabs with an amber line under the open one,
/// as Android's PrimaryTabRow (F13 a), no sideways scrolling. The row keeps the controls' side inset; when a
/// label does not fit its fifth, all five labels take the next smaller text role together (F13).
struct ControlTabs: View {
    enum Tab: CaseIterable {
        case layers, scene, emblem, text, draw
    }

    @State private var tab = Tab.layers
    /// Full opacity: the open tab shows, the others stay alive but unseen.
    private static let shown = 1.0
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t

    var body: some View {
        VStack(spacing: 0) {
            ViewThatFits(in: .horizontal) {
                tabRow(.heading)
                tabRow(.note)
            }
            .padding(.horizontal, Spacing.lg)
            Rectangle().fill(t.outline).frame(height: Sizes.swatchRing / 2)
            // Every tab stays alive, so each keeps its scroll position and open groups while another shows.
            ZStack {
                tabView(.layers) { LayersTab() }
                tabView(.scene) { SceneTab() }
                tabView(.emblem) { EmblemTab() }
                tabView(.text) { TextTab() }
                tabView(.draw) { DrawTab() }
            }
        }
        // Drawing on the picture only while the Draw tab is shown; moving only while its tab is (as on Android).
        .onChange(of: tab) { _, now in
            if now != .draw { model.setDrawTool(nil) }
            if let target = model.moveTarget, now != tabFor(target) { model.setMoveTarget(nil) }
        }
    }

    private func tabFor(_ target: MoveTarget) -> Tab {
        switch target {
        case .emblem: .emblem
        case .text: .text
        default: .layers
        }
    }

    private func tabRow(_ role: TextRole) -> some View {
        EqualWidthRow {
            ForEach(Tab.allCases, id: \.self) { option in
                Button {
                    tab = option
                } label: {
                    Text(label(option)).textRole(role).lineLimit(1).fixedSize()
                        .foregroundStyle(option == tab ? t.accent : t.textMuted)
                        .padding(.horizontal, Spacing.xs)
                        .padding(.vertical, Spacing.md)
                        .frame(maxWidth: .infinity)
                        .overlay(alignment: .bottom) {
                            if option == tab { Rectangle().fill(t.accent).frame(height: Sizes.progress) }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(option == tab ? [.isSelected, .isHeader] : .isHeader)
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

/// Lays its children out side by side in equal widths. Its ideal width is the widest child's times their
/// count, so `ViewThatFits` takes it only when every child fits its share.
struct EqualWidthRow: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ideal = subviews.map { $0.sizeThatFits(.unspecified) }
        let widest = ideal.map(\.width).max() ?? 0
        let height = ideal.map(\.height).max() ?? 0
        return CGSize(width: proposal.width ?? widest * CGFloat(subviews.count), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard !subviews.isEmpty else { return }
        let share = bounds.width / CGFloat(subviews.count)
        for (i, view) in subviews.enumerated() {
            view.place(at: CGPoint(x: bounds.minX + share * CGFloat(i), y: bounds.minY), proposal: ProposedViewSize(width: share, height: bounds.height))
        }
    }
}
