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
        let organic = max(0, min(1, s.organic))
        var g: EmblemGeometry
        switch s.motif {
        case .om:           g = om(organic: organic)
        case .treeOfLife:   g = treeOfLife(seed: s.seed, branching: s.branching, roots: s.roots, organic: organic)
        case .flowerOfLife: g = flowerOfLife(s.flowerVariant)
        case .lotus:        g = lotus(petals: s.petals, organic: organic)
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

    /// One cubic Bézier from p to q with tangents along `d0` at the start and `d1` at the end.
    private static func cubic(_ p: (x: Double, y: Double), _ c1: (x: Double, y: Double),
                              _ c2: (x: Double, y: Double), _ q: (x: Double, y: Double),
                              w0: Double, w1: Double, steps: Int = 16) -> [EmblemPoint] {
        (0...steps).map { k in
            let t = Double(k) / Double(steps), u = 1 - t
            return EmblemPoint(x: u*u*u*p.x + 3*u*u*t*c1.x + 3*u*t*t*c2.x + t*t*t*q.x,
                               y: u*u*u*p.y + 3*u*u*t*c1.y + 3*u*t*t*c2.y + t*t*t*q.y,
                               w: w0 + (w1 - w0) * t)
        }
    }

    private static func circle(radius r: Double, width w: Double, cx: Double = 0, cy: Double = 0,
                               steps: Int = 180) -> [EmblemPoint] {
        (0...steps).map { i in
            let a = Double(i) / Double(steps) * .pi * 2
            return EmblemPoint(x: cx + cos(a) * r, y: cy + sin(a) * r, w: w)
        }
    }

    private static func unit(_ x: Double, _ y: Double) -> (x: Double, y: Double) {
        let l = max(1e-9, hypot(x, y))
        return (x / l, y / l)
    }

    /// Calligraphic pen: thick where the stroke runs across the nib, thin along it.
    private static func penWidths(_ pts: [EmblemPoint], organic: Double, nib: Double = .pi * 0.2) -> [EmblemPoint] {
        guard organic > 0, pts.count > 2 else { return pts }
        return pts.indices.map { i in
            let a = pts[max(0, i - 1)], b = pts[min(pts.count - 1, i + 1)]
            let dir = atan2(b.y - a.y, b.x - a.x)
            let pen = 0.35 + 1.1 * abs(sin(dir - nib))
            var p = pts[i]
            p.w *= (1 - organic) + organic * pen
            return p
        }
    }

    // MARK: Om

    /// Own drawing of the Om sign: the "3" body, the curl to the right, the crescent and the dot.
    /// `organic` turns the even strokes into those of a broad calligraphy pen.
    static func om(organic: Double = 0) -> EmblemGeometry {
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
        g.strokes = [body, curl, crescent].map { penWidths(cubicChain($0).map(place), organic: organic) }
        g.dots = [EmblemDot(x: (0.34 + dx) * scale, y: (-0.70 + dy) * scale, r: 0.075 * scale)]
        return g
    }

    // MARK: Tree of Life

    private final class Branch {
        var pos: (x: Double, y: Double)
        var count: Int
        var kids: [Branch]
        init(pos: (x: Double, y: Double), count: Int, kids: [Branch]) {
            self.pos = pos; self.count = count; self.kids = kids
        }
    }

    /// A tree whose twigs and roots end evenly spread on the ring. The twig tips are placed on the
    /// ring first; neighbouring tips are then joined pairwise (sometimes in threes) towards the
    /// trunk, which gives the even fan of the classic Tree of Life. `organic` lets the branches
    /// flow out of each other in curves, sways the trunk and lets the crown reach further down.
    static func treeOfLife(seed: UInt64, branching: Double, roots: Bool, organic: Double = 0) -> EmblemGeometry {
        var rng = SeededRNG(seed: seed)
        var g = EmblemGeometry()
        let top = (x: 0.0, y: -0.10), bottom = (x: 0.0, y: 0.32)
        let sway = organic * rng.nextDouble(in: -0.06...0.06)
        g.strokes.append(cubicChain([
            [0, bottom.y + 0.04, 0.13], [sway, 0.22], [-sway, 0.02], [top.x, top.y, 0.095],
        ], steps: 20))
        let twigs = 8 + Int((max(0, min(1, branching)) * 32).rounded())
        let reach = 0.10 * organic
        branches(into: &g, rng: &rng, origin: top, up: true,
                 from: -.pi * (0.92 + reach), to: -.pi * (0.08 - reach),
                 tips: twigs, maxWidth: 0.085, pull: 0.85, organic: organic)
        if roots {
            branches(into: &g, rng: &rng, origin: bottom, up: false,
                     from: .pi * (0.14 - reach * 0.4), to: .pi * (0.86 + reach * 0.4),
                     tips: max(4, twigs / 2), maxWidth: 0.08, pull: 0.7, organic: organic)
        }
        return g
    }

    private static func branches(into g: inout EmblemGeometry, rng: inout SeededRNG,
                                 origin: (x: Double, y: Double), up: Bool, from lo: Double, to hi: Double,
                                 tips n: Int, maxWidth: Double, pull: Double, organic: Double) {
        let step = (hi - lo) / Double(n)
        var tipPoints: [(x: Double, y: Double)] = []
        for i in 0..<n {
            let a = lo + step * (Double(i) + 0.5) + rng.nextDouble(in: -0.3...0.3) * step
            tipPoints.append((cos(a) * ringRadius, sin(a) * ringRadius))
        }
        func width(_ count: Int) -> Double { 0.012 + maxWidth * (Double(count) / Double(n)).squareRoot() }

        // Build the branching from the tips towards the trunk.
        func node(_ a: Int, _ b: Int, depth: Int) -> Branch {
            if b - a == 1 { return Branch(pos: tipPoints[a], count: 1, kids: []) }
            var bounds: [Int]
            if b - a >= 4 && rng.nextDouble() < 0.3 {
                let third = Double(b - a) / 3
                bounds = [a, a + Int(third.rounded()), a + Int((third * 2).rounded()), b]
            } else {
                let mid = Double(a + b) / 2 + rng.nextDouble(in: -0.2...0.2) * Double(b - a)
                bounds = [a, min(b - 1, max(a + 1, Int(mid.rounded()))), b]
            }
            let kids = (0..<(bounds.count - 1)).map { node(bounds[$0], bounds[$0 + 1], depth: depth + 1) }
            let count = kids.reduce(0) { $0 + $1.count }
            let mx = kids.reduce(0.0) { $0 + $1.pos.x * Double($1.count) } / Double(count)
            let my = kids.reduce(0.0) { $0 + $1.pos.y * Double($1.count) } / Double(count)
            let f = depth > 0 ? pull * pow(1 - Double(count) / Double(n), 0.6) : 0
            return Branch(pos: (origin.x + (mx - origin.x) * f, origin.y + (my - origin.y) * f), count: count, kids: kids)
        }
        let root = node(0, n, depth: 0)

        // Direction in which a branch leaves its junction: towards the weighted middle of its twigs.
        func heading(_ b: Branch) -> (x: Double, y: Double) {
            if b.kids.isEmpty { return unit(b.pos.x, b.pos.y) }   // twigs meet the ring radially
            let mx = b.kids.reduce(0.0) { $0 + $1.pos.x * Double($1.count) } / Double(b.count)
            let my = b.kids.reduce(0.0) { $0 + $1.pos.y * Double($1.count) } / Double(b.count)
            return unit(mx - b.pos.x, my - b.pos.y)
        }

        // Draw from the trunk outwards so every branch continues the direction it came from.
        func draw(_ b: Branch, incoming: (x: Double, y: Double)) {
            for kid in b.kids {
                let p = b.pos, q = kid.pos
                let len = hypot(q.x - p.x, q.y - p.y)
                let chord = unit(q.x - p.x, q.y - p.y)
                // Start along the parent's direction (organic) or straight at the target.
                let d0 = unit(chord.x + (incoming.x - chord.x) * organic, chord.y + (incoming.y - chord.y) * organic)
                let h = heading(kid)
                let d1 = unit(chord.x + (h.x - chord.x) * organic * 0.8, chord.y + (h.y - chord.y) * organic * 0.8)
                let bend = rng.nextDouble(in: -0.18...0.18) * (1 - organic * 0.4)
                let k0 = 0.3 + 0.15 * organic, k1 = 0.3
                let c1 = (x: p.x + d0.x * len * k0 - chord.y * bend * len, y: p.y + d0.y * len * k0 + chord.x * bend * len)
                let c2 = (x: q.x - d1.x * len * k1 - chord.y * bend * len, y: q.y - d1.y * len * k1 + chord.x * bend * len)
                let w0 = width(kid.count) * (1 + 0.2 * organic)
                let w1 = kid.count > 1 ? width(kid.count) * 0.8 : 0.008
                g.strokes.append(cubic(p, c1, c2, q, w0: w0, w1: w1))
                draw(kid, incoming: d1)
            }
        }
        draw(root, incoming: up ? (0, -1) : (0, 1))
    }

    // MARK: Flower of Life

    /// The circles of the sacred-geometry family on a hexagonal grid, a corner pointing up.
    static func flowerOfLife(_ variant: EmblemFlowerVariant) -> EmblemGeometry {
        var g = EmblemGeometry()
        let line = 0.022
        let angles = (0..<6).map { -Double.pi / 2 + Double($0) * .pi / 3 }
        switch variant {
        case .seed:
            let r = 0.42
            g.strokes.append(circle(radius: r, width: line))
            for a in angles { g.strokes.append(circle(radius: r, width: line, cx: cos(a) * r, cy: sin(a) * r)) }
        case .flower:
            let r = 0.84 / 3
            let outer = 3 * r
            // Lattice points along two grid axes; full circles up to two steps out.
            for i in -3...3 {
                for j in -3...3 {
                    let x = r * (Double(i) * cos(angles[0]) + Double(j) * cos(angles[1]))
                    let y = r * (Double(i) * sin(angles[0]) + Double(j) * sin(angles[1]))
                    let d = hypot(x, y)
                    if d <= 2 * r + 1e-6 {
                        g.strokes.append(circle(radius: r, width: line, cx: x, cy: y))
                    } else if d <= 3 * r + 1e-6 {
                        // The next ring shows only as arcs inside the enclosing circle.
                        var run: [EmblemPoint] = []
                        for p in circle(radius: r, width: line * 0.8, cx: x, cy: y, steps: 240) {
                            if hypot(p.x, p.y) <= outer + 1e-6 { run.append(p) }
                            else if !run.isEmpty { if run.count > 1 { g.strokes.append(run) }; run = [] }
                        }
                        if run.count > 1 { g.strokes.append(run) }
                    }
                }
            }
            g.strokes.append(circle(radius: outer, width: line * 1.4))
        case .fruit, .metatron:
            let r = 0.84 / 5
            var centres: [(x: Double, y: Double)] = [(0, 0)]
            for a in angles { centres.append((cos(a) * 2 * r, sin(a) * 2 * r)) }
            for a in angles { centres.append((cos(a) * 4 * r, sin(a) * 4 * r)) }
            for c in centres { g.strokes.append(circle(radius: r, width: line, cx: c.x, cy: c.y)) }
            if variant == .metatron {
                for i in 0..<centres.count {
                    for j in (i + 1)..<centres.count {
                        g.strokes.append([EmblemPoint(x: centres[i].x, y: centres[i].y, w: line * 0.6),
                                          EmblemPoint(x: centres[j].x, y: centres[j].y, w: line * 0.6)])
                    }
                }
            }
        }
        return g
    }

    // MARK: Lotus

    private struct PetalShape {
        var angle, r0, r1, halfWidth, curl: Double

        /// Full, rounded body and a short point, like a lotus petal.
        func halfWidth(at t: Double) -> Double { halfWidth * pow(sin(.pi * pow(max(0, min(1, t)), 0.85)), 0.6) }

        /// Whether a point lies inside the petal (with a small margin so cut lines meet cleanly).
        func contains(_ x: Double, _ y: Double) -> Bool {
            let ca = cos(-angle), sa = sin(-angle)
            let u = x * ca - y * sa, v = x * sa + y * ca
            let t = (u - r0) / (r1 - r0)
            guard t > 0.01, t < 0.99 else { return false }
            return abs(v - curl * t * t) < halfWidth(at: t) - 0.004
        }

        func outline(width w: Double) -> [EmblemPoint] {
            let steps = 40
            var right: [(Double, Double)] = [], left: [(Double, Double)] = []
            for i in 0...steps {
                let t = Double(i) / Double(steps)
                let u = r0 + (r1 - r0) * t, shift = curl * t * t, h = halfWidth(at: t)
                right.append((u, shift + h)); left.append((u, shift - h))
            }
            let pts = right + left.reversed()
            let ca = cos(angle), sa = sin(angle)
            return pts.map { EmblemPoint(x: $0.0 * ca - $0.1 * sa, y: $0.0 * sa + $0.1 * ca, w: w) }
        }

        func vein(width w: Double) -> [EmblemPoint] {
            let ca = cos(angle), sa = sin(angle)
            return (0...20).map { i in
                let t = 0.08 + Double(i) / 20 * 0.62
                let u = r0 + (r1 - r0) * t, v = curl * t * t
                return EmblemPoint(x: u * ca - v * sa, y: u * sa + v * ca, w: w * (1 - 0.6 * Double(i) / 20))
            }
        }
    }

    /// Keeps the parts of a line that no occluder covers.
    private static func visibleRuns(_ pts: [EmblemPoint], hiddenBy hides: (Double, Double) -> Bool) -> [[EmblemPoint]] {
        var runs: [[EmblemPoint]] = []
        var run: [EmblemPoint] = []
        for p in pts {
            if hides(p.x, p.y) {
                if run.count > 1 { runs.append(run) }
                run = []
            } else {
                run.append(p)
            }
        }
        if run.count > 1 { runs.append(run) }
        return runs
    }

    /// A lotus seen from above: three rings of pointed petals around the seed pod. Inner petals
    /// lie in front of outer ones. `organic` gives the petals uneven sizes, a curved tip and a vein.
    static func lotus(petals: Int, organic: Double = 0) -> EmblemGeometry {
        var g = EmblemGeometry()
        var rng = SeededRNG(seed: 0x10705)
        let n = max(4, petals)
        let podRadius = 0.14
        let rings: [(count: Int, r0: Double, r1: Double, offset: Double, width: Double)] = [
            (n, 0.10, 0.52, 0.5, 0.026),      // inner, in front
            (n, 0.18, 0.74, 0.0, 0.024),
            (n * 2, 0.30, 0.88, 0.25, 0.020), // outer, behind
        ]
        var front: [PetalShape] = []
        for ring in rings {
            // Petals of one ring touch at their widest point.
            let half = .pi * (ring.r0 + ring.r1) / 2 / Double(ring.count)
            var shapes: [PetalShape] = []
            for i in 0..<ring.count {
                let a = (Double(i) + ring.offset) / Double(ring.count) * .pi * 2 - .pi / 2
                let grow = 1 + organic * rng.nextDouble(in: -0.08...0.06)
                let curl = organic * rng.nextDouble(in: -0.06...0.06)
                shapes.append(PetalShape(angle: a, r0: ring.r0, r1: ring.r0 + (ring.r1 - ring.r0) * grow,
                                         halfWidth: half, curl: curl))
            }
            let covered: (Double, Double) -> Bool = { x, y in
                hypot(x, y) < podRadius || front.contains { $0.contains(x, y) }
            }
            for shape in shapes {
                g.strokes += visibleRuns(shape.outline(width: ring.width), hiddenBy: covered)
                if organic > 0.2 {
                    g.strokes += visibleRuns(shape.vein(width: ring.width * 0.6), hiddenBy: covered)
                }
            }
            front += shapes
        }
        // Seed pod with its seeds.
        g.strokes.append(circle(radius: podRadius, width: 0.026))
        g.dots.append(EmblemDot(x: 0, y: 0, r: 0.026))
        for i in 0..<6 {
            let a = Double(i) / 6 * .pi * 2 - .pi / 2
            g.dots.append(EmblemDot(x: cos(a) * 0.08, y: sin(a) * 0.08, r: 0.02))
        }
        return g
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

    /// Pointed lotus petal as a closed outline, pointing outwards at `angle`. `curl` bends the
    /// tip sideways; with `closed` false only the middle line is returned.
    private static func petal(angle: Double, from r0: Double, to r1: Double, halfWidth: Double,
                              width w: Double, curl: Double = 0, closed: Bool = true) -> [EmblemPoint] {
        var mid: [(Double, Double)] = []
        var side: [(Double, Double)] = []
        for i in 0...20 {
            let t = Double(i) / 20
            let shift = curl * t * t
            mid.append((r0 + (r1 - r0) * t, shift))
            side.append((r0 + (r1 - r0) * t, shift + halfWidth * sin(.pi * pow(t, 0.7))))
        }
        let outline: [(Double, Double)]
        if closed {
            let other = mid.indices.reversed().map { (mid[$0].0, 2 * mid[$0].1 - side[$0].1) }
            outline = side + other
        } else {
            outline = mid
        }
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
