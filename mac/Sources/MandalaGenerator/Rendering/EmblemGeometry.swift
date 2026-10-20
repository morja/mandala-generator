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
    /// Areas filled in the Filled rendering; each entry is a set of polygons filled even-odd,
    /// so an inner polygon cuts a hole. Their outlines come from `strokes`.
    var fills: [[[(x: Double, y: Double)]]] = []

    mutating func append(_ other: EmblemGeometry) {
        strokes += other.strokes
        dots += other.dots
        fills += other.fills
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
        case .yinYang:      g = yinYang()
        case .dharmaWheel:  g = dharmaWheel()
        case .unalome:      g = unalome(organic: organic)
        case .tripleMoon:   g = tripleMoon()
        case .sriYantra:    g = sriYantra()
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
    /// flow out of each other in gnarled curves, adds side twigs and rootlets, sways the trunk and
    /// lets the crown reach further down. More branching gives more and finer twigs.
    static func treeOfLife(seed: UInt64, branching: Double, roots: Bool, organic: Double = 0) -> EmblemGeometry {
        var rng = SeededRNG(seed: seed)
        var g = EmblemGeometry()
        let top = (x: 0.0, y: -0.10), bottom = (x: 0.0, y: 0.32)
        let sway = organic * rng.nextDouble(in: -0.06...0.06)
        // The trunk ends exactly where the roots start and leaves both ends vertically, so roots
        // and crown join without a kink; only its middle sways.
        let midY = (bottom.y + top.y) / 2
        g.strokes.append(cubicChain([
            [bottom.x, bottom.y, 0.13], [0, bottom.y - 0.07], [sway, midY + 0.07], [sway, midY, 0.085],
            [sway, midY - 0.07], [0, top.y + 0.07], [top.x, top.y, 0.095],
        ], steps: 14))
        let b = max(0, min(1, branching))
        let twigs = 8 + Int((b * 56).rounded())
        let reach = 0.10 * organic
        branches(into: &g, rng: &rng, origin: top, up: true,
                 from: -.pi * (0.92 + reach), to: -.pi * (0.08 - reach),
                 tips: twigs, maxWidth: 0.085, pull: 0.85, organic: organic, fineness: b)
        if roots {
            branches(into: &g, rng: &rng, origin: bottom, up: false,
                     from: .pi * (0.14 - reach * 0.4), to: .pi * (0.86 + reach * 0.4),
                     tips: max(4, twigs / 2), maxWidth: 0.08, pull: 0.7, organic: organic, fineness: b)
        }
        return g
    }

    private static func branches(into g: inout EmblemGeometry, rng: inout SeededRNG,
                                 origin: (x: Double, y: Double), up: Bool, from lo: Double, to hi: Double,
                                 tips n: Int, maxWidth: Double, pull: Double, organic: Double, fineness: Double) {
        let step = (hi - lo) / Double(n)
        var tipPoints: [(x: Double, y: Double)] = []
        for i in 0..<n {
            let a = lo + step * (Double(i) + 0.5) + rng.nextDouble(in: -0.3...0.3) * step
            tipPoints.append((cos(a) * ringRadius, sin(a) * ringRadius))
        }
        // Twigs get finer the more of them there are.
        let twigWidth = 0.012 - 0.006 * fineness
        let tipWidth = 0.008 - 0.004 * fineness
        func width(_ count: Int) -> Double { twigWidth + maxWidth * (Double(count) / Double(n)).squareRoot() }

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

        /// Bends a branch gently back and forth, keeping both ends in place.
        func gnarl(_ pts: [EmblemPoint], length: Double) -> [EmblemPoint] {
            guard organic > 0, pts.count > 2 else { return pts }
            let amp = min(0.018, organic * 0.06 * length)
            let freq = rng.nextDouble(in: 0.7...1.5), phase = rng.nextDouble(in: 0...(2 * .pi))
            return pts.indices.map { i in
                let a = pts[max(0, i - 1)], b = pts[min(pts.count - 1, i + 1)]
                let n = unit(-(b.y - a.y), b.x - a.x)
                let t = Double(i) / Double(pts.count - 1)
                let off = amp * sin(.pi * t) * sin(2 * .pi * freq * t + phase)
                var p = pts[i]
                p.x += n.x * off; p.y += n.y * off
                return p
            }
        }

        /// Short side twigs (or rootlets) that grow off a branch and stop before the ring.
        func sprigs(along pts: [EmblemPoint], count: Int) {
            guard organic > 0.1, pts.count > 4 else { return }
            let chance = organic * (0.25 + 0.6 * fineness) * (count <= 2 ? 1.0 : 0.6)
            for _ in 0..<2 where rng.nextDouble() < chance {
                let i = Int(Double(pts.count - 1) * rng.nextDouble(in: 0.3...0.8))
                let a = pts[max(0, i - 1)], b = pts[min(pts.count - 1, i + 1)]
                let dir = atan2(b.y - a.y, b.x - a.x) + (rng.nextDouble() < 0.5 ? -1 : 1) * rng.nextDouble(in: 0.45...0.8)
                let len = rng.nextDouble(in: 0.04...0.09) * (1 - 0.3 * fineness)
                let p = (x: pts[i].x, y: pts[i].y)
                let q = (x: p.x + cos(dir) * len, y: p.y + sin(dir) * len)
                guard hypot(q.x, q.y) < ringRadius - 0.04 else { continue }
                let bend = rng.nextDouble(in: -0.25...0.25) * len
                let c = (x: (p.x + q.x) / 2 - sin(dir) * bend, y: (p.y + q.y) / 2 + cos(dir) * bend)
                let w0 = max(tipWidth, pts[i].w * 0.45)
                g.strokes.append(cubic(p, c, c, q, w0: w0, w1: tipWidth * 0.6, steps: 10))
            }
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
                let w1 = kid.count > 1 ? width(kid.count) * 0.8 : tipWidth
                let pts = gnarl(cubic(p, c1, c2, q, w0: w0, w1: w1, steps: 24), length: len)
                g.strokes.append(pts)
                if b !== root { sprigs(along: pts, count: kid.count) }   // none right at the trunk
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
        /// Yantra petal: full width where it sits on the circle, curving in to a point like an arch.
        var arch = false

        /// Lotus: full, rounded body and a short point. Arch: widest at the base.
        func halfWidth(at t: Double) -> Double {
            let t = max(0, min(1, t))
            if arch { return halfWidth * pow(sin(.pi / 2 * (1 - t)), 0.55) * (1 + 0.18 * sin(.pi * t)) }
            return halfWidth * pow(sin(.pi * pow(t, 0.85)), 0.6)
        }

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

    // MARK: Yin Yang

    /// Yin and yang, exact and closed. For an open brush circle around it, use the Ensō frame.
    static func yinYang() -> EmblemGeometry {
        var g = EmblemGeometry()
        let r = 0.70, h = r / 2, w = 0.03
        func arc(_ cx: Double, _ cy: Double, _ rad: Double, from a0: Double, to a1: Double, steps: Int = 90) -> [(x: Double, y: Double)] {
            (0...steps).map { i in
                let a = a0 + (a1 - a0) * Double(i) / Double(steps)
                return (cx + cos(a) * rad, cy + sin(a) * rad)
            }
        }
        // The S: upper half bulges left, lower half bulges right.
        let s = arc(0, -h, h, from: -.pi / 2, to: -1.5 * .pi) + arc(0, h, h, from: -.pi / 2, to: .pi / 2).dropFirst()
        g.strokes.append(circle(radius: r, width: w))
        g.strokes.append(s.map { EmblemPoint(x: $0.x, y: $0.y, w: w * 0.8) })
        // Filled half: the right side of the circle and back along the S, with the other eye cut out.
        let eye = r * 0.13
        let half = arc(0, 0, r, from: -.pi / 2, to: .pi / 2) + s.reversed()
        g.fills.append([half, arc(0, -h, eye, from: 0, to: 2 * .pi, steps: 48)])
        g.strokes.append(circle(radius: eye, width: w * 0.7, cx: 0, cy: -h, steps: 60))
        g.dots.append(EmblemDot(x: 0, y: h, r: eye))

        return g
    }

    // MARK: Dharma wheel

    /// The eight-spoked wheel: double rim, hub and spindle-shaped spokes with knobs outside.
    static func dharmaWheel() -> EmblemGeometry {
        var g = EmblemGeometry()
        let outer = 0.72, inner = 0.60, hub = 0.15
        g.strokes.append(circle(radius: outer, width: 0.035))
        g.strokes.append(circle(radius: inner, width: 0.026))
        g.strokes.append(circle(radius: hub, width: 0.03))
        g.dots.append(EmblemDot(x: 0, y: 0, r: 0.055))
        for k in 0..<8 {
            let a = -Double.pi / 2 + Double(k) * .pi / 4
            let spoke: [EmblemPoint] = (0...24).map { i in
                let t = Double(i) / 24
                let rr = hub + (inner - hub) * t
                // Spindle: thin at both ends, widest a third of the way out.
                let bulge = sin(.pi * pow(t, 0.75))
                return EmblemPoint(x: cos(a) * rr, y: sin(a) * rr, w: 0.018 + 0.05 * bulge)
            }
            g.strokes.append(spoke)
            g.strokes.append([EmblemPoint(x: cos(a) * outer, y: sin(a) * outer, w: 0.03),
                              EmblemPoint(x: cos(a) * 0.79, y: sin(a) * 0.79, w: 0.03)])
            g.dots.append(EmblemDot(x: cos(a) * 0.82, y: sin(a) * 0.82, r: 0.045))
        }
        return g
    }

    // MARK: Unalome

    /// The unalome: a spiral at the bottom, loops that grow smaller on the way up, a straight
    /// line and three dots. `organic` gives it the strokes of a broad pen.
    static func unalome(organic: Double = 0) -> EmblemGeometry {
        var g = EmblemGeometry()
        let w = 0.032
        var pts: [EmblemPoint] = []
        // Spiral from the centre outwards, ending at its top.
        let cx = 0.0, cy = 0.50, spiral = 0.17, turns = 2.5
        for i in 0...200 {
            let t = Double(i) / 200
            let a = .pi / 2 + t * turns * 2 * .pi
            pts.append(EmblemPoint(x: cx + cos(a) * spiral * t, y: cy + sin(a) * spiral * t, w: w * (0.4 + 0.6 * t)))
        }
        // Loops climbing upwards, each smaller than the one before.
        let loops = 3.0, yStart = cy - spiral, yTop = -0.25
        for i in 1...400 {
            let t = Double(i) / 400
            let th = t * loops * 2 * .pi
            let amp = 0.20 * (1 - 0.65 * t)
            let back = 0.10 * (1 - 0.6 * t)
            pts.append(EmblemPoint(x: amp * sin(th),
                                   y: yStart + (yTop - yStart) * t + back * cos(th) - back,
                                   w: w))
        }
        // Straight line to the top.
        if let last = pts.last {
            for i in 1...20 {
                let t = Double(i) / 20
                pts.append(EmblemPoint(x: last.x * (1 - t), y: last.y + (-0.58 - last.y) * t, w: w * (1 - 0.3 * t)))
            }
        }
        g.strokes.append(penWidths(pts, organic: organic))
        for y in [-0.67, -0.75, -0.83] { g.dots.append(EmblemDot(x: 0, y: y, r: 0.026)) }
        return g
    }

    // MARK: Triple moon

    /// Waxing crescent, full moon, waning crescent.
    static func tripleMoon() -> EmblemGeometry {
        var g = EmblemGeometry()
        let r = 0.24, w = 0.026
        g.strokes.append(circle(radius: r, width: w, steps: 120))
        g.fills.append([(0...120).map { i in
            let a = Double(i) / 120 * 2 * .pi
            return (cos(a) * r, sin(a) * r)
        }])
        for side in [-1.0, 1.0] {
            // A crescent with its back to the full moon: the disc minus one shifted outwards.
            let cx = side * 0.58, d = 0.13
            let phi = atan2((r * r - d * d / 4).squareRoot(), d / 2)
            var poly: [(x: Double, y: Double)] = []
            for i in 0...80 {
                let a = phi + (2 * .pi - 2 * phi) * Double(i) / 80
                poly.append((cx + side * cos(a) * r, sin(a) * r))
            }
            for i in 0...60 {
                let a = (.pi + phi) - 2 * phi * Double(i) / 60
                poly.append((cx + side * (d + cos(a) * r), sin(a) * r))
            }
            g.fills.append([poly])
            g.strokes.append((poly + [poly[0]]).map { EmblemPoint(x: $0.x, y: $0.y, w: w) })
        }
        return g
    }

    // MARK: Sri Yantra

    /// The nine interlocking triangles (four pointing up, five down) with the bindu, inside two
    /// lotus rings of 8 and 16 petals. Triangle coordinates from Bhaskar Tripathi, "Sri Yantra",
    /// TeXample.net (CC BY-SA 4.0): left x, base y, apex y, right x in a circle of radius 100
    /// around (150, 150), y down. Checked 20 October 2026: 29 points where three or more lines
    /// meet exactly; six near misses of 0.13 (of 200), below a pixel at any export size.
    static func sriYantra() -> EmblemGeometry {
        var g = EmblemGeometry()
        let triangles: [(l: Double, base: Double, apex: Double, r: Double)] = [
            (53.65669559977147, 123.20508075688774, 250, 246.34330440022853),                    // down
            (52.984011026495736, 174.24660560764943, 50, 247.01598897350425),                   // up
            (98.71823312733801, 220.03828947357886, 123.20508075688774, 201.281766872662),      // up
            (78.26467997914015, 197.92315674002487, 78.10499177949904, 221.73532002085986),     // up
            (90.4856922951427, 78.10499177949904, 160.66014976539617, 209.51430770485734),      // down
            (80.98384838952128, 103.12199145016105, 220.03828947357886, 219.0161516104787),     // down
            (114.9488500600036, 160.66014976539617, 103.12199145016105, 185.0511499399964),     // up
            (116.35142605010424, 134.30757626706648, 197.92315674002487, 183.64857394989576),   // down
            (124.61190803072795, 144.79777263138968, 174.24660560764943, 175.38809196927207),   // down
        ]
        let inner = 0.46
        func map(_ x: Double, _ y: Double) -> (Double, Double) { ((x - 150) / 100 * inner, (y - 150) / 100 * inner) }
        for t in triangles {
            let pts = [map(t.l, t.base), map(150, t.apex), map(t.r, t.base), map(t.l, t.base)]
            g.strokes.append(pts.map { EmblemPoint(x: $0.0, y: $0.1, w: 0.012) })
        }
        g.strokes.append(circle(radius: inner, width: 0.016))
        g.dots.append(EmblemDot(x: 0, y: 0, r: 0.022))
        // Lotus rings: 8 petals, then 16, each closed by a circle.
        for (count, r0, r1, offset) in [(8, inner, 0.65, 0.5), (16, 0.665, 0.83, 0.25)] {
            // Neighbouring petals touch where they sit on the circle.
            let half = .pi * r0 / Double(count)
            for i in 0..<count {
                let a = (Double(i) + offset) / Double(count) * .pi * 2 - .pi / 2
                g.strokes.append(PetalShape(angle: a, r0: r0, r1: r1, halfWidth: half, curl: 0, arch: true).outline(width: 0.014))
            }
            g.strokes.append(circle(radius: r1 + 0.012, width: 0.014))
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
        case .enso:
            g.append(enso(radius: r))
        }
        return g
    }

    /// Ensō: an open circle in one brush stroke. It starts heavy, thins out along the way and
    /// ends in a few dry bristle traces; the gap stays open.
    private static func enso(radius r: Double) -> EmblemGeometry {
        var g = EmblemGeometry()
        let start = -Double.pi * 0.58          // upper left, where the brush is set down
        let sweep = 2 * Double.pi * 0.9        // leaves a tenth of the circle open
        let steps = 260
        func point(_ t: Double, _ dr: Double) -> (Double, Double) {
            // The brush drifts slightly inwards towards the end, as a hand does.
            let a = start + sweep * t
            let rr = r - 0.03 * t * t + dr
            return (cos(a) * rr, sin(a) * rr)
        }
        // Main stroke: a heavy, round start, a full body, then thinning out.
        var main: [EmblemPoint] = []
        for i in 0...steps {
            let t = Double(i) / Double(steps)
            // Swells quickly where the brush is pressed down, then loses ink towards the end.
            let w = t < 0.06 ? 0.06 + 0.04 * t / 0.06 : 0.10 * (1 - 0.85 * pow(t, 1.3))
            let (x, y) = point(t, 0)
            main.append(EmblemPoint(x: x, y: y, w: max(0.006, w)))
        }
        g.strokes.append(main)
        // Dry brush: bristle traces that split off over the last third and stop one after another.
        for (offset, endT, w) in [(-0.028, 0.97, 0.010), (-0.012, 1.0, 0.012), (0.010, 0.93, 0.009), (0.026, 0.88, 0.007)] {
            var trace: [EmblemPoint] = []
            for i in 0...80 {
                let t = 0.62 + (endT - 0.62) * Double(i) / 80
                let spread = offset * min(1, (t - 0.62) / 0.12)
                let (x, y) = point(t, spread)
                trace.append(EmblemPoint(x: x, y: y, w: w * (1 - 0.7 * Double(i) / 80)))
            }
            g.strokes.append(trace)
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
