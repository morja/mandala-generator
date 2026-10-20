import Foundation

struct NoiseUtils {

    // MARK: - 1D smooth noise via sum of sines
    static func smooth1D(_ t: Double, freq: Double, seed: Int) -> Double {
        let s = Double(seed &* 2654435761 &+ 1013904223)
        var result = 0.0
        var amplitude = 0.5
        var frequency = freq
        for i in 0..<4 {
            let phase = Double((seed &+ i * 7919) & 0x7FFFFFFF) * 0.000000002326
            result += sin(t * frequency + phase + s * 0.0001) * amplitude
            amplitude *= 0.5
            frequency *= 2.0
        }
        // Normalize to 0-1
        return (result + 1.0) * 0.5
    }

    // MARK: - 2D smooth noise using gradient lattice
    static func smooth2D(_ x: Double, _ y: Double, seed: Int) -> Double {
        let xi = Int(floor(x))
        let yi = Int(floor(y))
        let xf = x - floor(x)
        let yf = y - floor(y)

        let u = fade(xf)
        let v = fade(yf)

        let n00 = grad2D(hash2D(xi,     yi,     seed), xf,       yf)
        let n10 = grad2D(hash2D(xi + 1, yi,     seed), xf - 1.0, yf)
        let n01 = grad2D(hash2D(xi,     yi + 1, seed), xf,       yf - 1.0)
        let n11 = grad2D(hash2D(xi + 1, yi + 1, seed), xf - 1.0, yf - 1.0)

        let x1 = lerp(n00, n10, u)
        let x2 = lerp(n01, n11, u)
        return (lerp(x1, x2, v) + 1.0) * 0.5
    }

    // MARK: - Fractional Brownian Motion
    static func fbm2D(_ x: Double, _ y: Double, octaves: Int, seed: Int) -> Double {
        var value = 0.0
        var amplitude = 0.5
        var frequency = 1.0
        var cx = x
        var cy = y
        for o in 0..<octaves {
            value += smooth2D(cx * frequency, cy * frequency, seed: seed &+ o * 1000003) * amplitude
            amplitude *= 0.5
            frequency *= 2.0
            cx += 0.31
            cy += 0.17
        }
        return value
    }

    // MARK: - Helpers

    private static func fade(_ t: Double) -> Double {
        return t * t * t * (t * (t * 6 - 15) + 10)
    }

    private static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double {
        return a + t * (b - a)
    }

    private static func hash2D(_ x: Int, _ y: Int, _ seed: Int) -> Int {
        var h = seed &+ x &* 374761393 &+ y &* 668265263
        h = (h ^ (h >> 13)) &* 1274126177
        return h ^ (h >> 16)
    }

    private static func grad2D(_ hash: Int, _ x: Double, _ y: Double) -> Double {
        let h = hash & 3
        let u = h < 2 ? x : y
        let v = h < 2 ? y : x
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v)
    }
}
