import Foundation

// MARK: - Emblem geometry
//
// Motifs and frames are centre lines with a width at every point, in a unit space with y pointing
// down and the frame ring at radius 0.92. The renderer turns them into lines, outlines, filled
// shapes or beads, so every motif works in every rendering.

struct EmblemPoint {
    var x: Double
    var y: Double
    var w: Double
}

struct EmblemDot {
    var x: Double
    var y: Double
    var r: Double
}

struct EmblemGeometry {
    var strokes: [[EmblemPoint]] = []
    var dots: [EmblemDot] = []

    mutating func append(_ other: EmblemGeometry) {
        strokes += other.strokes
        dots += other.dots
    }
}

enum EmblemShapes {

    /// Radius of the frame ring; tree twigs end on it.
    static let ringRadius = 0.92

    static func geometry(for s: EmblemLayerSettings) -> EmblemGeometry {
        var g: EmblemGeometry
        switch s.motif {
        case .om:         g = om()
        case .treeOfLife: g = treeOfLife(seed: s.seed, branching: s.branching, roots: s.roots)
        }
        g.append(frame(s.frame))
        return g
    }

    // MARK: Curves

    /// Samples a chain of cubic Béziers. Anchors are [x, y, width], control points [x, y]:
    /// P0, C1, C2, P1, C1, C2, P2 … Widths ease between anchors.
    static func cubicChain(_ c: [[Double]], steps: Int = 24) -> [EmblemPoint] {
        var out: [EmblemPoint] = []
        var i = 0
        while i + 3 < c.count {
            let p0 = c[i], c1 = c[i + 1], c2 = c[i + 2], p1 = c[i + 3]
            let last = i + 3 == c.count - 1
            for k in 0..<(steps + (last ? 1 : 0)) {
                let t = Double(k) / Double(steps), u = 1 - t
                let x = u*u*u*p0[0] + 3*u*u*t*c1[0] + 3*u*t*t*c2[0] + t*t*t*p1[0]
                let y = u*u*u*p0[1] + 3*u*u*t*c1[1] + 3*u*t*t*c2[1] + t*t*t*p1[1]
                let e = t * t * (3 - 2 * t)
                out.append(EmblemPoint(x: x, y: y, w: p0[2] + (p1[2] - p0[2]) * e))
            }
            i += 3
        }
        return out
    }

    private static func circle(radius r: Double, width w: Double, steps: Int = 180) -> [EmblemPoint] {
        (0...steps).map { i in
            let a = Double(i) / Double(steps) * .pi * 2
            return EmblemPoint(x: cos(a) * r, y: sin(a) * r, w: w)
        }
    }

    // MARK: Om

    /// Own drawing of the Om sign: the "3" body, the curl to the right, the crescent and the dot.
    static func om() -> EmblemGeometry {
        let body: [[Double]] = [
            [-0.62, -0.36, 0.05], [-0.56, -0.60], [-0.24, -0.64], [-0.15, -0.42, 0.14],
            [-0.07, -0.23], [-0.22, -0.09], [-0.40, -0.07, 0.08],
            [-0.10, -0.10], [0.03, 0.08], [0.01, 0.25, 0.17],
            [-0.01, 0.48], [-0.30, 0.57], [-0.48, 0.45, 0.12],
            [-0.62, 0.37], [-0.68, 0.22], [-0.60, 0.14, 0.05],
        ]
        let curl: [[Double]] = [
            [0.0, 0.14, 0.07], [0.12, 0.02], [0.24, -0.10], [0.40, -0.10, 0.12],
            [0.58, -0.10], [0.72, 0.04], [0.70, 0.20, 0.15],
            [0.68, 0.38], [0.50, 0.46], [0.36, 0.42, 0.12],
            [0.22, 0.38], [0.18, 0.26], [0.26, 0.18, 0.07],
            [0.32, 0.13], [0.40, 0.16], [0.42, 0.22, 0.03],
        ]
        let crescent: [[Double]] = [
            [0.02, -0.56, 0.03], [0.05, -0.40], [0.20, -0.33], [0.34, -0.33, 0.10],
            [0.48, -0.33], [0.63, -0.40], [0.66, -0.56, 0.03],
        ]
        // Drawn in a box around (0.02, -0.10); centre it and leave room inside the ring.
        let scale = 0.8, dx = -0.02, dy = 0.10
        func place(_ p: EmblemPoint) -> EmblemPoint {
            EmblemPoint(x: (p.x + dx) * scale, y: (p.y + dy) * scale, w: p.w * scale)
        }
        var g = EmblemGeometry()
        g.strokes = [body, curl, crescent].map { cubicChain($0).map(place) }
        g.dots = [EmblemDot(x: (0.34 + dx) * scale, y: (-0.70 + dy) * scale, r: 0.075 * scale)]
        return g
    }

    // MARK: Tree of Life

    /// A tree whose twigs and roots end evenly spread on the ring. The twig tips are placed on the
    /// ring first; neighbouring tips are then joined pairwise (sometimes in threes) towards the
    /// trunk, which gives the even, organic fan of the classic Tree of Life.
    static func treeOfLife(seed: UInt64, branching: Double, roots: Bool) -> EmblemGeometry {
        var rng = SeededRNG(seed: seed)
        var g = EmblemGeometry()
        let top = (x: 0.0, y: -0.10), bottom = (x: 0.0, y: 0.32)
        g.strokes.append([
            EmblemPoint(x: 0, y: bottom.y + 0.03, w: 0.12),
            EmblemPoint(x: 0, y: 0.20, w: 0.085),
            EmblemPoint(x: 0, y: 0.05, w: 0.08),
            EmblemPoint(x: 0, y: top.y, w: 0.095),
        ])
        let twigs = 8 + Int((max(0, min(1, branching)) * 32).rounded())
        branches(into: &g, rng: &rng, origin: top, from: -.pi * 0.92, to: -.pi * 0.08,
                 tips: twigs, maxWidth: 0.085, pull: 0.85)
        if roots {
            branches(into: &g, rng: &rng, origin: bottom, from: .pi * 0.14, to: .pi * 0.86,
                     tips: max(4, twigs / 2), maxWidth: 0.08, pull: 0.7)
        }
        return g
    }

    private static func branches(into g: inout EmblemGeometry, rng: inout SeededRNG,
                                 origin: (x: Double, y: Double), from lo: Double, to hi: Double,
                                 tips n: Int, maxWidth: Double, pull: Double) {
        let step = (hi - lo) / Double(n)
        var tipPoints: [(x: Double, y: Double)] = []
        for i in 0..<n {
            let a = lo + step * (Double(i) + 0.5) + rng.nextDouble(in: -0.3...0.3) * step
            tipPoints.append((cos(a) * ringRadius, sin(a) * ringRadius))
        }
        func width(_ count: Int) -> Double { 0.012 + maxWidth * (Double(count) / Double(n)).squareRoot() }

        func edge(_ p: (x: Double, y: Double), _ q: (x: Double, y: Double), _ w0: Double, _ w1: Double) {
            let dx = q.x - p.x, dy = q.y - p.y
            let bend = rng.nextDouble(in: -0.18...0.18)
            let cx = (p.x + q.x) / 2 - dy * bend, cy = (p.y + q.y) / 2 + dx * bend
            var pts: [EmblemPoint] = []
            for k in 0...14 {
                let t = Double(k) / 14, u = 1 - t
                pts.append(EmblemPoint(x: u*u*p.x + 2*u*t*cx + t*t*q.x,
                                       y: u*u*p.y + 2*u*t*cy + t*t*q.y,
                                       w: w0 + (w1 - w0) * t))
            }
            g.strokes.append(pts)
        }

        // Returns the junction point and the number of tips below it.
        func node(_ a: Int, _ b: Int, depth: Int) -> ((x: Double, y: Double), Int) {
            if b - a == 1 { return (tipPoints[a], 1) }
            var bounds: [Int]
            if b - a >= 4 && rng.nextDouble() < 0.3 {
                let third = Double(b - a) / 3
                bounds = [a, a + Int(third.rounded()), a + Int((third * 2).rounded()), b]
            } else {
                let mid = Double(a + b) / 2 + rng.nextDouble(in: -0.2...0.2) * Double(b - a)
                bounds = [a, min(b - 1, max(a + 1, Int(mid.rounded()))), b]
            }
            let kids = (0..<(bounds.count - 1)).map { node(bounds[$0], bounds[$0 + 1], depth: depth + 1) }
            let count = kids.reduce(0) { $0 + $1.1 }
            let mx = kids.reduce(0.0) { $0 + $1.0.x * Double($1.1) } / Double(count)
            let my = kids.reduce(0.0) { $0 + $1.0.y * Double($1.1) } / Double(count)
            let f = depth > 0 ? pull * pow(1 - Double(count) / Double(n), 0.6) : 0
            let pos = (x: origin.x + (mx - origin.x) * f, y: origin.y + (my - origin.y) * f)
            for (p, c) in kids {
                edge(pos, p, width(c), c > 1 ? width(c) * 0.8 : 0.008)
            }
            return (pos, count)
        }
        _ = node(0, n, depth: 0)
    }

    // MARK: Frames

    static func frame(_ f: EmblemFrame) -> EmblemGeometry {
        var g = EmblemGeometry()
        let r = ringRadius
        switch f {
        case .none:
            break
        case .ring:
            g.strokes.append(circle(radius: r, width: 0.035))
        case .doubleRing:
            g.strokes.append(circle(radius: r, width: 0.035))
            g.strokes.append(circle(radius: r + 0.08, width: 0.018))
        case .lotus:
            g.strokes.append(circle(radius: r, width: 0.035))
            let petals = 16
            for i in 0..<petals {
                g.strokes.append(petal(angle: Double(i) / Double(petals) * .pi * 2,
                                       from: r + 0.02, to: r + 0.30, halfWidth: 0.16, width: 0.018))
            }
            g.strokes.append(circle(radius: r + 0.34, width: 0.012))
        case .gates:
            g.strokes.append(circle(radius: r, width: 0.035))
            for (i, s) in [1.06, 1.14, 1.22].enumerated() {
                g.strokes.append(gateSquare(half: s, gate: 0.26, step: 0.09, width: i == 1 ? 0.022 : 0.014))
            }
        }
        return g
    }

    /// Pointed lotus petal as a closed outline, pointing outwards at `angle`.
    private static func petal(angle: Double, from r0: Double, to r1: Double,
                              halfWidth: Double, width w: Double) -> [EmblemPoint] {
        var side: [(Double, Double)] = []
        for i in 0...20 {
            let t = Double(i) / 20
            side.append((r0 + (r1 - r0) * t, halfWidth * sin(.pi * pow(t, 0.7))))
        }
        let outline = side + side.reversed().map { ($0.0, -$0.1) }
        let ca = cos(angle), sa = sin(angle)
        return outline.map { EmblemPoint(x: $0.0 * ca - $0.1 * sa, y: $0.0 * sa + $0.1 * ca, w: w) }
    }

    /// Square with a T-shaped gate in the middle of each side (the bhupura of yantras).
    private static func gateSquare(half s: Double, gate g: Double, step t: Double, width w: Double) -> [EmblemPoint] {
        let side: [(Double, Double)] = [
            (s, -s), (s, -g - t), (s + t, -g - t), (s + t, -g), (s + 2 * t, -g),
            (s + 2 * t, g), (s + t, g), (s + t, g + t), (s, g + t),
        ]
        var pts: [EmblemPoint] = []
        for k in 0..<4 {
            let a = Double(k) * .pi / 2, ca = cos(a), sa = sin(a)
            pts += side.map { EmblemPoint(x: $0.0 * ca - $0.1 * sa, y: $0.0 * sa + $0.1 * ca, w: w) }
        }
        pts.append(pts[0])
        return pts
    }
}
