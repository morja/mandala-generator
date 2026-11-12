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

    /// Falls back to the folder `check-full` writes when the test is started from Xcode.
    private static func folderFromFile() -> String? {
        let repo = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = repo.appendingPathComponent("ios/build/parity-reference").path
        return FileManager.default.fileExists(atPath: folder) ? folder : nil
    }
}
