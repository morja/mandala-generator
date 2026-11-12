import SwiftUI
@preconcurrency import MandalaCore

// The tour over the whole editor (Android's `TourOverlay`, DM-22 card a): everything but the picture and the step's
// targets is dimmed, a ring marks each target and a thin line leads from it to the card docked at the bottom.
// Touches outside the card do nothing.

/// Where the controls are: each marked control reports its bounds under its target.
struct TourBoundsKey: PreferenceKey {
    static let defaultValue: [TourTarget: Anchor<CGRect>] = [:]

    static func reduce(value: inout [TourTarget: Anchor<CGRect>], nextValue: () -> [TourTarget: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

private struct TourAnchor: ViewModifier {
    let target: TourTarget?
    @Environment(TourModel.self) private var tour

    func body(content: Content) -> some View {
        // Only while a tour runs, so scrolling does not rebuild the overlay's inputs otherwise.
        let running = tour.running
        content.anchorPreference(key: TourBoundsKey.self, value: .bounds) { running && target != nil ? [target!: $0] : [:] }
    }
}

extension View {
    /// Reports this view's place on screen as `target` while a tour runs.
    func tourAnchor(_ target: TourTarget?) -> some View { modifier(TourAnchor(target: target)) }
}

/// A ring in the overlay's coordinates.
private struct Ring {
    let rect: CGRect
    let round: Bool
    let line: Bool
}

struct TourOverlay: View {
    let state: TourState
    let bounds: [TourTarget: CGRect]
    let size: CGSize
    let safe: EdgeInsets
    @Environment(TourModel.self) private var tour
    @Environment(\.tokens) private var t
    @Environment(\.colorScheme) private var scheme
    @State private var cardHeight: CGFloat = 0
    /// Kept while the controls scroll to the next row, so the card does not jump with it.
    @State private var lastTop: CGFloat?

    /// How dark the dimming is; the dark theme needs more to set the card apart (DM-22).
    private static let scrimLight = 0.42
    private static let scrimDark = 0.62

    /// Icons get a round ring; rows and tabs follow their own shape.
    private static let roundTargets: Set<TourTarget> = [.play, .favourites, .save, .more, .topDie, .layerDie]

    var body: some View {
        let step = state.step
        let settled = tour.settled == step
        let rings = settled ? rings(of: step) : []
        let open = settled ? step.open.compactMap { bounds[$0] } : []
        let picture = bounds[.picture]
        let top = safe.top + Spacing.md
        let bottom = size.height - safe.bottom - Spacing.md
        let docked = cardTop(rings: rings, top: top, bottom: bottom, settled: settled)
        let width = size.width - 2 * Spacing.md

        ZStack(alignment: .topLeading) {
            Canvas { ctx, canvasSize in
                let corner = Radius.md
                let all = CGRect(origin: .zero, size: canvasSize)
                ctx.fill(Path(all), with: .color(t.canvas.opacity(scheme == .dark ? Self.scrimDark : Self.scrimLight)))

                // Holes: the picture, the targets and what the step leaves open.
                ctx.blendMode = .clear
                if let picture { ctx.fill(Path(roundedRect: picture, cornerRadius: corner), with: .color(t.canvas)) }
                for r in open { ctx.fill(Path(roundedRect: r, cornerRadius: corner), with: .color(t.canvas)) }
                for ring in rings { ctx.fill(shape(ring, corner), with: .color(t.canvas)) }
                ctx.blendMode = .normal

                // Rings and the lines from them to the card.
                let line = Sizes.swatchRing
                for ring in rings {
                    // Inside the hole: half the line would otherwise fall on the dimming.
                    let drawn = Ring(rect: ring.rect.insetBy(dx: line / 2, dy: line / 2), round: ring.round, line: ring.line)
                    ctx.stroke(shape(drawn, corner), with: .color(t.accent), lineWidth: line)
                    guard ring.line else { continue }
                    let x = ring.rect.midX
                    guard x >= Spacing.md, x <= canvasSize.width - Spacing.md else { continue }
                    let from = docked >= ring.rect.maxY ? ring.rect.maxY : docked + cardHeight
                    let to = docked >= ring.rect.maxY ? docked : ring.rect.minY
                    if to - from >= Spacing.lg {
                        var path = Path()
                        path.move(to: CGPoint(x: x, y: from))
                        path.addLine(to: CGPoint(x: x, y: to))
                        ctx.stroke(path, with: .color(t.accent), lineWidth: line)
                    }
                }
            }
            // Takes every touch outside the card, so nothing behind it changes during the tour.
            .contentShape(Rectangle())
            .onTapGesture {}

            TourCard(state: state, onNext: next, onBack: tour.back, onSkip: end)
                .frame(width: width)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { cardHeight = $0 }
                .offset(x: Spacing.md, y: docked)
        }
        .frame(width: size.width, height: size.height)
        .onChange(of: settled ? docked : nil) { _, y in
            if let y { lastTop = y }
        }
        .onChange(of: required(rings: rings, picture: picture, bottom: bottom, settled: settled)) { _, need in
            if need > tour.shrinkBy + 1 { tour.shrinkBy = need }
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityLabel(Texts.tourPane)
        .accessibilityAction(.escape) { tour.end() }
        .onChange(of: state.step, initial: true) { _, step in
            AccessibilityNotification.Announcement(Texts.tourTitle(step) + ". " + Texts.tourBody(step, freeLayers: Int(ProFeatures.shared.FREE_LAYERS))).post()
        }
    }

    private func rings(of step: TourStep) -> [Ring] {
        let icon = Sizes.icon / 2 + Spacing.sm
        return step.marks.compactMap { mark in
            guard let b = bounds[mark.target] else { return nil }
            let round = Self.roundTargets.contains(mark.target)
            let rect = round ? CGRect(x: b.midX - icon, y: b.midY - icon, width: icon * 2, height: icon * 2) : b.insetBy(dx: -Spacing.xs, dy: -Spacing.xs)
            return Ring(rect: rect, round: round, line: mark.line)
        }
    }

    private func shape(_ ring: Ring, _ corner: CGFloat) -> Path {
        ring.round ? Path(ellipseIn: ring.rect) : Path(roundedRect: ring.rect, cornerRadius: corner)
    }

    private func cardTop(rings: [Ring], top: CGFloat, bottom: CGFloat, settled: Bool) -> CGFloat {
        if !settled, let last = lastTop { return last }
        let spans = rings.map { TourLayout.Span(top: $0.rect.minY, bottom: $0.rect.maxY) }
        return CGFloat(TourLayout.cardTop(rings: spans, cardHeight: cardHeight, top: top, bottom: bottom, gap: Spacing.sm))
            .clampedToFinite()
    }

    /// Not enough room under the picture and the marked row: the picture folds smaller by what is missing.
    private func required(rings: [Ring], picture: CGRect?, bottom: CGFloat, settled: Bool) -> Double {
        guard settled, let picture, tour.pictureFull > 0 else { return 0 }
        let below = rings.filter { $0.rect.minY >= picture.minY }.map(\.rect.maxY).max() ?? 0
        let lowest = max(picture.maxY, below)
        let missing = lowest + Spacing.sm + cardHeight - bottom
        return tour.pictureFull - picture.width + missing
    }

    private func next() { tour.next() }

    private func end() { tour.end() }
}

private extension CGFloat {
    func clampedToFinite() -> CGFloat { isFinite ? self : 0 }
}

/// The card: title and step counter, one or two sentences, and the buttons.
private struct TourCard: View {
    let state: TourState
    let onNext: () -> Void
    let onBack: () -> Void
    let onSkip: () -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        let step = state.step
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(Texts.tourTitle(step)).textRole(.title).foregroundStyle(t.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(Texts.tourCount(state.number, TourState.count)).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
            }
            Text(Texts.tourBody(step, freeLayers: Int(ProFeatures.shared.FREE_LAYERS))).textRole(.body).foregroundStyle(t.text)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, Spacing.xs)
            if step == .die { diePlaces.padding(.top, Spacing.md) }
            buttons.padding(.top, Spacing.sm)
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .padding(.bottom, Spacing.sm)
        .background(t.surfaceRaised, in: RoundedRectangle(cornerRadius: Radius.md))
        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(t.outline, lineWidth: Sizes.swatchRing / 2))
        // Swallows taps between the buttons, so they do not reach the dimming behind.
        .contentShape(RoundedRectangle(cornerRadius: Radius.md))
        .onTapGesture {}
        .accessibilityElement(children: .contain)
    }

    /// The other places with a die, as the die each shows with its name.
    private var diePlaces: some View {
        let places: [(String, String)] = [
            ("dice", Texts.tourPlaceEffects), ("dice", Texts.tabEmblem), ("dice", Texts.tabText),
            ("die.face.3", Texts.tourPlaceMenu), ("die.face.1", Texts.tourPlacePlay),
        ]
        return FlowLayout {
            ForEach(places, id: \.1) { place in
                VStack(spacing: Spacing.xs) {
                    Image(systemName: place.0).foregroundStyle(t.accent).frame(width: Sizes.icon, height: Sizes.icon)
                    Text(place.1).textRole(.note).foregroundStyle(t.textMuted).lineLimit(1)
                }
                .padding(.horizontal, Spacing.sm)
                .padding(.bottom, Spacing.xs)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Skip on the left, Back and Next on the right, in one line when they fit. Otherwise (long words at large
    /// text) Back and Next keep the line and Skip goes below them, so no label wraps.
    private var buttons: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.sm) {
                if !state.isLast { skip }
                Spacer(minLength: Spacing.md)
                actions
            }
            VStack(alignment: .trailing, spacing: Spacing.xs) {
                actions
                if !state.isLast { skip.frame(maxWidth: .infinity, alignment: .leading) }
            }
        }
    }

    private var skip: some View {
        Button(action: onSkip) { label(Texts.tourSkip, muted: true) }.buttonStyle(.plain)
    }

    private var actions: some View {
        HStack(spacing: Spacing.sm) {
            if !state.isFirst {
                Button(action: onBack) { label(Texts.tourBack, muted: true) }.buttonStyle(.plain)
            }
            Button(action: onNext) {
                Text(state.isLast ? Texts.tourDone : Texts.tourNext).textRole(.heading).lineLimit(1).fixedSize()
                    .foregroundStyle(t.onAccent)
                    .padding(.horizontal, Spacing.lg)
                    .frame(minHeight: Sizes.icon * 2)
                    .background(t.accent, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private func label(_ text: String, muted: Bool) -> some View {
        Text(text).textRole(.heading).lineLimit(1).fixedSize()
            .foregroundStyle(muted ? t.textMuted : t.text)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Sizes.icon * 2)
            .contentShape(Rectangle())
    }
}
