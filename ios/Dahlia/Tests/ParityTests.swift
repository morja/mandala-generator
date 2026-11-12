import Foundation
import MandalaCore
import XCTest

/// Renders the fixtures through the iOS framework and compares them with the JVM renders written by
/// `ParityReference.kt` (PLAN-4 section 3.11). Run by `ios/scripts/check-full`, which passes the folder
/// of the references in `PARITY_REFERENCE`. Identical, or PSNR at least `minPSNR`, where floats round
/// differently: a few bytes off by one or two (SPIKE-3: 96.3 dB for the favourite; QA-7: 82.9 dB is the
/// lowest of the rounding cases, a few hundred bytes off by one).
final class ParityTests: XCTestCase {
    static let minPSNR = 80.0

    /// Fixtures known to differ more, with the threshold they must still reach. The strange attractor
    /// iterates a chaotic map, so the last-bit differences between Kotlin/Native and the JVM grow into a
    /// different orbit (QA-7 finding 3). Remove the entry when core makes it match.
    static let knownDivergent: [String: Double] = ["style-strange-attractor@512": 15.0]

    func testFixturesMatchTheJVM() throws {
        guard let folder = ProcessInfo.processInfo.environment["PARITY_REFERENCE"] ?? Self.folderFromFile() else {
            throw XCTSkip("PARITY_REFERENCE is not set")
        }
        let base = URL(fileURLWithPath: folder)
        let manifest = try String(contentsOf: base.appendingPathComponent("cases.txt"), encoding: .utf8)
        let bridge = IosBridge.shared
        var identical = 0
        var worst = Double.infinity
        var report: [String] = []
        var failures: [String] = []

        for line in manifest.split(separator: "\n") {
            let parts = line.split(separator: " ", maxSplits: 3).map(String.init)
            let name = parts[0], size = Int32(parts[1])!, glow = Int32(parts[2])!, path = parts[3]
            let params = bridge.decode(json: try String(contentsOfFile: path, encoding: .utf8))
            let result = bridge.render(params: params, size: size, supersample: true, glowReferenceWidth: glow,
                                       threads: Int32(bridge.processors()))
            let ours = [UInt8](bridge.rgba(image: result.image))
            let theirs = [UInt8](try Data(contentsOf: base.appendingPathComponent("\(name)-\(size).rgba")))
            guard ours.count == theirs.count else {
                failures.append("\(name): size \(ours.count) bytes against \(theirs.count)")
                continue
            }
            var squares = 0.0
            var maxDiff = 0
            var differing = 0
            for i in 0..<ours.count where ours[i] != theirs[i] {
                let d = abs(Int(ours[i]) - Int(theirs[i]))
                squares += Double(d * d)
                maxDiff = max(maxDiff, d)
                differing += 1
            }
            if differing == 0 {
                identical += 1
                report.append("PARITY \(name)@\(size) identical")
            } else {
                let psnr = 10 * log10(255.0 * 255.0 / (squares / Double(ours.count)))
                if Self.knownDivergent["\(name)@\(size)"] == nil { worst = min(worst, psnr) }
                report.append(String(format: "PARITY %@@%d psnr=%.1f dB maxDiff=%d differingBytes=%d", name, size, psnr, maxDiff, differing))
                let key = "\(name)@\(size)"
                if psnr < (Self.knownDivergent[key] ?? Self.minPSNR) { failures.append("\(key): \(psnr) dB") }
            }
        }
        report.append("PARITY total=\(report.count) identical=\(identical) worstPSNR(without known)=\(worst.isInfinite ? "none differs" : String(format: "%.1f dB", worst))")
        for line in report { print(line) }
        if let out = ProcessInfo.processInfo.environment["PARITY_REPORT"] {
            try? report.joined(separator: "\n").write(toFile: out, atomically: true, encoding: .utf8)
        }
        XCTAssertTrue(failures.isEmpty, "Below \(Self.minPSNR) dB or wrong size: \(failures)")
    }

    /// Text cases (starter favourites with quotes, one fixture per font): the iPhone draws the letters with
    /// Core Text, the JVM with Java2D, both with the same Mac fonts. Lines sit on the same pixels (no shift
    /// matches better than none), but the glyph edges are smoothed differently, and white letters on a dark
    /// picture make every edge count, so the whole picture is held to a low threshold (PLAN-4 section 3.11;
    /// measured 21.7–45.2 dB in IOS-5).
    static let minTextPSNR = 19.0

    /// Zapfino: Core Text applies the font's contextual swashes as the Mac's AppKit does, Java2D does not, so
    /// the lines are wider and break after other words (IOS-5).
    static let knownTextDivergent: [String: Double] = ["text-zapfino@512": 13.0]

    /// How far an edge of the text's box may lie from the JVM's, in pixels: glow and shadow edges spread with
    /// the glyphs' weight (measured 0–6 px in IOS-5); a line out of place would be about a line height (30 px).
    static let maxBoxOffset = 8

    struct Box: CustomStringConvertible {
        var minX: Int, maxX: Int, minY: Int, maxY: Int
        var description: String { "\(minX)-\(maxX)x\(minY)-\(maxY)" }
    }

    /// The box of pixels where `picture` differs clearly from `plain` (RGBA, `side` wide).
    static func textBox(_ picture: [UInt8], _ plain: [UInt8], _ side: Int) -> Box? {
        var box: Box?
        for y in 0..<side {
            for x in 0..<side {
                let i = (y * side + x) * 4
                let d = abs(Int(picture[i]) - Int(plain[i])) + abs(Int(picture[i + 1]) - Int(plain[i + 1])) + abs(Int(picture[i + 2]) - Int(plain[i + 2]))
                guard d > 48 else { continue }
                if box == nil { box = Box(minX: x, maxX: x, minY: y, maxY: y) }
                box!.minX = min(box!.minX, x); box!.maxX = max(box!.maxX, x)
                box!.minY = min(box!.minY, y); box!.maxY = max(box!.maxY, y)
            }
        }
        return box
    }

    func testTextCasesMatchTheJVM() throws {
        guard let folder = ProcessInfo.processInfo.environment["PARITY_REFERENCE"] ?? Self.folderFromFile() else {
            throw XCTSkip("PARITY_REFERENCE is not set")
        }
        let base = URL(fileURLWithPath: folder)
        let manifest = try String(contentsOf: base.appendingPathComponent("text-cases.txt"), encoding: .utf8)
        let bridge = IosBridge.shared
        let renderer = CoreTextRenderer()
        var report: [String] = []
        var failures: [String] = []
        var worst = Double.infinity
        for line in manifest.split(separator: "\n") {
            let parts = line.split(separator: " ", maxSplits: 3).map(String.init)
            let name = parts[0], size = Int32(parts[1])!, glow = Int32(parts[2])!, path = parts[3]
            let params = bridge.decode(json: try String(contentsOfFile: path, encoding: .utf8))
            let result = bridge.renderWithText(params: params, size: size, supersample: true, glowReferenceWidth: glow,
                                               threads: Int32(bridge.processors()), textRenderer: renderer)
            let ours = [UInt8](bridge.rgba(image: result.image))
            let theirs = [UInt8](try Data(contentsOf: base.appendingPathComponent("\(name)-\(size).rgba")))
            guard ours.count == theirs.count else {
                failures.append("\(name): size \(ours.count) bytes against \(theirs.count)")
                continue
            }
            var squares = 0.0
            var differingPixels = 0
            for p in stride(from: 0, to: ours.count, by: 4) {
                var pixelDiffers = false
                for c in 0..<3 where ours[p + c] != theirs[p + c] {
                    let d = Double(Int(ours[p + c]) - Int(theirs[p + c]))
                    squares += d * d
                    pixelDiffers = true
                }
                if pixelDiffers { differingPixels += 1 }
            }
            // PSNR over the colour channels (alpha is always 255).
            let mse = squares / Double(ours.count / 4 * 3)
            let psnr = mse == 0 ? Double.infinity : 10 * log10(255.0 * 255.0 / mse)
            worst = min(worst, psnr)
            report.append(String(format: "TEXTPARITY %@@%d psnr=%.1f dB differingPixels=%.2f%%", name, size, psnr, Double(differingPixels) * 100 / Double(ours.count / 4)))
            if psnr < (Self.knownTextDivergent["\(name)@\(size)"] ?? Self.minTextPSNR) { failures.append("\(name): \(psnr) dB") }

            // Where the text lies: the box of pixels the text changes, against the same picture without text
            // (the rest matches the JVM within rounding). PSNR alone would let a line off or upside down pass.
            let plain = bridge.renderWithText(params: IosEditingParts.shared.textSwitched(params: params, on: false), size: size, supersample: true,
                                              glowReferenceWidth: glow, threads: Int32(bridge.processors()), textRenderer: renderer)
            let without = [UInt8](bridge.rgba(image: plain.image))
            let w = Int(size)
            if let a = Self.textBox(ours, without, w), let b = Self.textBox(theirs, without, w) {
                let off = max(abs(a.minX - b.minX), abs(a.maxX - b.maxX), abs(a.minY - b.minY), abs(a.maxY - b.maxY))
                report.append("TEXTBOX \(name)@\(size) ios=\(a) jvm=\(b) worstEdge=\(off)px")
                if off > Self.maxBoxOffset && Self.knownTextDivergent["\(name)@\(size)"] == nil { failures.append("\(name): text box \(off) px off") }
            } else {
                failures.append("\(name): no text found")
            }
            // The iPhone's render for the report's side-by-side pictures, when asked.
            if let out = ProcessInfo.processInfo.environment["PARITY_TEXT_PICTURES"] {
                try? Data(ours).write(to: URL(fileURLWithPath: out).appendingPathComponent("\(name)-ios-\(size).rgba"))
            }
        }
        report.append(String(format: "TEXTPARITY total=%d worstPSNR=%.1f dB", report.filter { $0.hasPrefix("TEXTPARITY") }.count, worst))
        for line in report { print(line) }
        if let out = ProcessInfo.processInfo.environment["PARITY_TEXT_REPORT"] {
            try? report.joined(separator: "\n").write(toFile: out, atomically: true, encoding: .utf8)
        }
        XCTAssertTrue(failures.isEmpty, "Text cases below \(Self.minTextPSNR) dB or wrong size: \(failures)")
    }

    /// Falls back to the folder `check-full` writes when the test is started from Xcode.
    private static func folderFromFile() -> String? {
        let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = repo.appendingPathComponent("ios/build/parity-reference").path
        return FileManager.default.fileExists(atPath: folder) ? folder : nil
    }
}
