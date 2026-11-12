import SwiftUI
@preconcurrency import MandalaCore

/// The Pro sheet (ADR-0016, WP-9), laid out as Android's: the kit's `UnlockSheet` with Dahlia's `ProIntro`. The title, why
/// it opened (the Pro features the mandala uses, a note) and what Pro adds; then the buy button with the App Store's
/// price, "… without them", the App Store's feedback (pending, why there is no price, the last purchase or restore), and
/// "Try again" and "Restore purchase" one per line, so long translations fit. It is as tall as its content and scrolls
/// when that is taller than the screen.
struct ProSheet: View {
    let request: ProRequest
    @Environment(EditorModel.self) private var model
    @Environment(\.tokens) private var t
    @State private var height: CGFloat = 0

    private var store: ProStore { model.proStore }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(Texts.proTitle).textRole(.title).foregroundStyle(t.text)
                intro
                if store.unlocked {
                    Text(Texts.proUnlocked).textRole(.body).foregroundStyle(t.text)
                } else {
                    purchase
                    feedback
                    links
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xl)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        }
        .background(t.background)
        .presentationDetents(height > 0 ? [.height(height)] : [.medium])
        .presentationDragIndicator(.visible)
        .presentationBackground(t.background)
        // A purchase or restore closes the sheet; the thanks shows as a message (Android: LaunchedEffect(pro.unlocked)).
        .onChange(of: store.unlocked) { _, unlocked in if unlocked { model.closePro() } }
    }

    // ── Why, and what Pro adds (Android's ProIntro) ──

    private var intro: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if !request.features.isEmpty {
                Text(request.action != nil ? Texts.proUsesKeep : Texts.proUses).textRole(.body).foregroundStyle(t.text)
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(request.features, id: \.self) { feature in
                        Text(Texts.proFeatureItem(Texts.proFeature(feature))).textRole(.body).foregroundStyle(t.text)
                    }
                }
                .padding(.leading, Spacing.md)
            }
            if let note = request.note { Text(note).textRole(.body).foregroundStyle(t.text) }
            Text(Texts.proSummary).textRole(.note).foregroundStyle(t.textMuted)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    // ── The purchase ──

    private var buyLabel: String {
        if let price = store.price { return Texts.proBuyFor(price) }
        return store.loadingPrice ? Texts.proLoadingPrice : Texts.proBuy
    }

    private var purchase: some View {
        VStack(spacing: Spacing.sm) {
            // The button keeps its place while the price loads or is missing; the reason stands under it.
            Button { Task { await store.buy() } } label: {
                Text(buyLabel).textRole(.heading).lineLimit(1)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.xs)
            }
            .buttonStyle(.borderedProminent)
            .tint(t.accent)
            .foregroundStyle(t.onAccent)
            .disabled(!store.canBuy)
            if !request.features.isEmpty {
                Button { model.continueWithoutPro() } label: {
                    Text(request.action.map(Texts.proWithout) ?? Texts.proRemove).textRole(.heading).lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.xs)
                }
                .buttonStyle(.bordered)
                .tint(t.accent)
            }
        }
        .padding(.top, Spacing.sm)
    }

    /// A pending purchase shows once (the message repeats it), then why there is no price, then the last feedback.
    @ViewBuilder
    private var feedback: some View {
        let problem = store.loadingPrice ? nil : store.priceProblem
        let message = store.message.flatMap { $0 == .pending && store.pending ? nil : $0 }
        if store.pending || problem != nil || message != nil {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                if store.pending { Text(Texts.proPending).textRole(.note).foregroundStyle(t.textMuted) }
                if let problem { Text(Texts.proPriceProblem(problem)).textRole(.note).foregroundStyle(t.textMuted) }
                if let message { Text(Texts.proMessage(message)).textRole(.body).foregroundStyle(t.text) }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var links: some View {
        VStack(alignment: .leading, spacing: 0) {
            if store.priceProblem != nil && !store.loadingPrice {
                Button { Task { await store.loadPrice() } } label: {
                    Text(Texts.proRetry).textRole(.body).lineLimit(1)
                        .padding(.vertical, Spacing.sm)
                }
                .foregroundStyle(t.accent)
            }
            Button { Task { await store.restore() } } label: {
                Text(Texts.proRestore).textRole(.body).lineLimit(1)
                    .padding(.vertical, Spacing.sm)
            }
            .foregroundStyle(store.busy ? t.textMuted : t.accent)
            .disabled(store.busy)
        }
    }
}

extension View {
    /// Presents the Pro sheet while a Pro request is open. Only one presenter may show it at a time: the editor while no
    /// other sheet is open, otherwise the open sheet (save sheet, menu), so it appears on top of what asked for it.
    func proSheet(active: Bool = true) -> some View { modifier(ProSheetHost(active: active)) }
}

private struct ProSheetHost: ViewModifier {
    let active: Bool
    @Environment(EditorModel.self) private var model

    func body(content: Content) -> some View {
        content.sheet(item: Binding(get: { active ? model.save.proRequest : nil },
                                    set: { if $0 == nil && active { model.closePro() } })) { request in
            ProSheet(request: request)
        }
    }
}
