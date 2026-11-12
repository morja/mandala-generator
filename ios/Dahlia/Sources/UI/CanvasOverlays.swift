import SwiftUI
import UIKit
@preconcurrency import MandalaCore

// What lies on the picture while moving or drawing by hand (Android's `MoveOverlay` and `DrawingOverlay`).
// Both take the fingers, so holding the picture (without effects) only counts outside them.

/// Moves the selected layer, the emblem or the text with the fingers: drag moves, pinch sizes, two fingers
/// turn (not the text). A ring, or a line for the text, shows where it is at once; the preview follows when
/// the fingers rest. On a centre line a guide crosses the picture and the phone ticks once (UX-1 20).
struct MoveOverlay: View {
    let mark: MoveMark
    let onMove: (_ dx: Double, _ dy: Double, _ zoom: Double, _ turnDegrees: Double) -> Void
    @Environment(\.tokens) private var t

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                Canvas { ctx, size in
                    // A dark edge under the accent keeps the marks visible over bright mandalas.
                    func line(_ from: CGPoint, _ to: CGPoint, _ width: CGFloat) {
                        var path = Path()
                        path.move(to: from)
                        path.addLine(to: to)
                        ctx.stroke(path, with: .color(t.canvas), style: StrokeStyle(lineWidth: width + Sizes.swatchRing * 2, lineCap: .round))
                        ctx.stroke(path, with: .color(t.accent), style: StrokeStyle(lineWidth: width, lineCap: .round))
                    }
                    let thin = Sizes.swatchRing / 2, thick = Sizes.swatchRing * 3 / 4
                    if mark.onCentreX { line(CGPoint(x: size.width / 2, y: 0), CGPoint(x: size.width / 2, y: size.height), thin) }
                    if mark.onCentreY { line(CGPoint(x: 0, y: size.height / 2), CGPoint(x: size.width, y: size.height / 2), thin) }
                    let c = CGPoint(x: mark.x * size.width, y: mark.y * size.height)
                    let r = mark.radius * size.width
                    if mark.target == .text {
                        line(CGPoint(x: c.x - r, y: c.y), CGPoint(x: c.x + r, y: c.y), thick)
                    } else {
                        let ring = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
                        ctx.stroke(ring, with: .color(t.canvas), lineWidth: thick + Sizes.swatchRing * 2)
                        ctx.stroke(ring, with: .color(t.accent), lineWidth: thick)
                    }
                    let dot = Sizes.swatchRing * 2
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - dot - thin, y: c.y - dot - thin, width: (dot + thin) * 2, height: (dot + thin) * 2)), with: .color(t.canvas))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - dot, y: c.y - dot, width: dot * 2, height: dot * 2)), with: .color(t.accent))
                }
                .allowsHitTesting(false)
                TransformCatcher { dx, dy, zoom, turn in
                    onMove(dx / max(w, 1), dy / max(h, 1), zoom, turn)
                }
            }
        }
        // One tick when the part comes onto a centre line, none when it leaves it.
        .sensoryFeedback(trigger: [mark.onCentreX, mark.onCentreY]) { old, new in
            (new[0] && !old[0]) || (new[1] && !old[1]) ? .selection : nil
        }
    }
}

/// Drag, pinch and turn with UIKit's recognisers, which follow the middle of two fingers like Android's
/// `detectTransformGestures`; each step reports what changed since the last one (points, a factor, degrees).
private struct TransformCatcher: UIViewRepresentable {
    let onStep: (_ dx: Double, _ dy: Double, _ zoom: Double, _ turnDegrees: Double) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.maximumNumberOfTouches = 2
        let pinch = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pinch(_:)))
        let turn = UIRotationGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.turn(_:)))
        for recogniser in [pan, pinch, turn] as [UIGestureRecognizer] {
            recogniser.delegate = context.coordinator
            view.addGestureRecognizer(recogniser)
        }
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.onStep = onStep
    }

    func makeCoordinator() -> Coordinator { Coordinator(onStep: onStep) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onStep: (Double, Double, Double, Double) -> Void
        // Pan, pinch and turn of one touch event are added up and sent as one step on the next run-loop
        // turn, as Android's `detectTransformGestures` reports them together; otherwise every finger
        // movement would change the settings up to three times.
        private var dx = 0.0, dy = 0.0, zoom = 1.0, turn = 0.0
        private var pending = false

        init(onStep: @escaping (Double, Double, Double, Double) -> Void) { self.onStep = onStep }

        /// Changes and the last movement at the end count; a cancelled gesture adds nothing.
        private func counts(_ g: UIGestureRecognizer) -> Bool { g.state == .changed || g.state == .ended }

        @objc func pan(_ g: UIPanGestureRecognizer) {
            guard counts(g), let view = g.view else { return }
            let d = g.translation(in: view)
            g.setTranslation(.zero, in: view)
            dx += d.x
            dy += d.y
            schedule()
        }

        @objc func pinch(_ g: UIPinchGestureRecognizer) {
            guard counts(g) else { return }
            zoom *= g.scale
            g.scale = 1
            schedule()
        }

        @objc func turn(_ g: UIRotationGestureRecognizer) {
            guard counts(g) else { return }
            // UIKit turns clockwise for positive radians on screen, as the shared rule expects.
            turn += g.rotation * 180 / .pi
            g.rotation = 0
            schedule()
        }

        private func schedule() {
            guard !pending else { return }
            pending = true
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                pending = false
                let step = (dx, dy, zoom, turn)
                (dx, dy, zoom, turn) = (0, 0, 1, 0)
                if step != (0, 0, 1, 0) { onStep(step.0, step.1, step.2, step.3) }
            }
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}

/// Catches finger strokes on the picture and shows the one in progress with its turned copies, with a guide
/// ray per copy. The finished stroke goes to the settings and appears in the next preview.
struct DrawingOverlay: View {
    let symmetry: Int
    let colour: Color
    /// Line width (lines) or brush diameter (spray) as a share of the picture's width.
    let width: Double
    let onStroke: ([Double], [Double]) -> Void
    @Environment(\.tokens) private var t
    @State private var xs: [Double] = []
    @State private var ys: [Double] = []
    /// Where the stroke in progress began: a new start means a new stroke, also when the last one was
    /// cancelled by the system without an end.
    @State private var startedAt: CGPoint?

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            Canvas { ctx, size in
                let c = CGPoint(x: size.width / 2, y: size.height / 2)
                let sym = max(1, symmetry)
                let reach = max(size.width, size.height)
                for k in 0..<sym {
                    let a = Double(k) * 2 * .pi / Double(sym)
                    var ray = Path()
                    ray.move(to: c)
                    ray.addLine(to: CGPoint(x: c.x + cos(a) * reach, y: c.y + sin(a) * reach))
                    ctx.stroke(ray, with: .color(t.accent.opacity(Self.guideAlpha)), lineWidth: Sizes.swatchRing / 2)
                }
                guard !xs.isEmpty else { return }
                let lineWidth = max(1, width * size.width)
                for k in 0..<sym {
                    let a = Double(k) * 2 * .pi / Double(sym)
                    let ca = cos(a), sa = sin(a)
                    func at(_ i: Int) -> CGPoint {
                        let dx = xs[i] * size.width - c.x, dy = ys[i] * size.height - c.y
                        return CGPoint(x: c.x + dx * ca - dy * sa, y: c.y + dx * sa + dy * ca)
                    }
                    if xs.count == 1 {
                        let p = at(0)
                        ctx.fill(Path(ellipseIn: CGRect(x: p.x - lineWidth / 2, y: p.y - lineWidth / 2, width: lineWidth, height: lineWidth)), with: .color(colour))
                    } else {
                        var path = Path()
                        path.move(to: at(0))
                        for i in 1..<xs.count { path.addLine(to: at(i)) }
                        ctx.stroke(path, with: .color(colour), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { g in
                    if startedAt != g.startLocation {
                        startedAt = g.startLocation
                        xs = []
                        ys = []
                    }
                    xs.append(min(max(g.location.x / max(size.width, 1), 0), 1))
                    ys.append(min(max(g.location.y / max(size.height, 1), 0), 1))
                }
                .onEnded { _ in
                    onStroke(xs, ys)
                    xs = []
                    ys = []
                    startedAt = nil
                })
        }
    }

    /// The guide rays are faint, as Android's 35 % accent.
    private static let guideAlpha = 0.35
}
