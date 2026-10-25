import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Reference renderer: mandala-ref <settings.json> <out.png> [outputSize]
let args = CommandLine.arguments

// Stable picker artwork, rendered on the Mac and shipped by both apps. No editor settings are read.
if args.count == 3, args[1] == "--style-previews" {
    let directory = URL(fileURLWithPath: args[2], isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    for style in MandalaStyle.allCases {
        var params = MandalaParameters()
        params.outputSize = 96
        params.resolvedPalettes = ColorPalettes.all
        params.layers = [StyleLayer(style: style, scale: 0.7, paletteIndex: 18,
                                   complexity: style == .strangeAttractor ? 0.15 : 0.35,
                                   density: style == .strangeAttractor ? 0.1 : 0.35,
                                   glowIntensity: 0.3, abstractLevel: 0, saturation: 0,
                                   brightness: style == .strangeAttractor ? 0.4 : 0.9, seed: 42)]
        let image = MandalaRenderer.render(params: params)
        guard let source = image.cgImage(forProposedRect: nil, context: nil, hints: nil),
              let context = CGContext(data: nil, width: 96, height: 96, bitsPerComponent: 8,
                                      bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { exit(1) }
        context.interpolationQuality = .high
        context.draw(source, in: CGRect(x: 0, y: 0, width: 96, height: 96))
        // Balance foreground brightness per example. A dense motif gets a quieter target than
        // sparse linework, so neither a white disc nor an almost black thumbnail dominates the picker.
        // This affects picker artwork only, never the mandala renderer or saved settings.
        guard let pixels = context.data?.assumingMemoryBound(to: UInt8.self) else { exit(1) }
        var histogram = [Int](repeating: 0, count: 256)
        for y in 0..<96 {
            for x in 0..<96 {
                histogram[Int(pixels[y * context.bytesPerRow + x * 4])] += 1
            }
        }
        guard let peak = histogram.lastIndex(where: { $0 > 0 }), peak > 0 else {
            print("Empty style preview: \(style.rawValue)"); exit(1)
        }
        let threshold = max(2, Int(Double(peak) * 0.02))
        let foreground = histogram.enumerated().filter { $0.offset > threshold }.reduce(0) { $0 + $1.element }
        let coverage = Double(foreground) / (96 * 96)
        let target = 180.0 * (1 - 0.45 * sqrt(coverage))
        var accumulated = 0
        var reference = peak
        for value in (threshold + 1)...255 {
            accumulated += histogram[value]
            if accumulated >= max(1, Int(Double(foreground) * 0.9)) {
                reference = value
                break
            }
        }
        let curve = (0...255).map { value -> UInt8 in
            let adjusted = target * pow(Double(value) / Double(max(1, reference)), 0.65)
            return UInt8(min(210, adjusted).rounded())
        }
        for y in 0..<96 {
            for x in 0..<96 {
                let offset = y * context.bytesPerRow + x * 4
                for channel in 0..<3 { pixels[offset + channel] = curve[Int(pixels[offset + channel])] }
            }
        }
        guard let small = context.makeImage(),
              let destination = CGImageDestinationCreateWithURL(
                directory.appendingPathComponent(style.rawValue + ".png") as CFURL,
                UTType.png.identifier as CFString, 1, nil) else { exit(1) }
        CGImageDestinationAddImage(destination, small, nil)
        guard CGImageDestinationFinalize(destination) else { exit(1) }
        print("preview: \(style.rawValue)")
    }
    exit(0)
}
guard args.count >= 3 else { print("usage: mandala-ref in.json out.png [size]"); exit(2) }
let data = try Data(contentsOf: URL(fileURLWithPath: args[1]))
var params = try JSONDecoder().decode(MandalaParameters.self, from: data)
params.resolvedPalettes = ColorPalettes.all
// Fixtures name background images relative to the repository root; the app stores file URLs.
if let url = params.baseLayer.imageURL, url.scheme == nil {
    params.baseLayer.imageURL = URL(fileURLWithPath: url.relativeString)
}
if args.count >= 4, let s = Int(args[3]) { params.outputSize = s }
let start = Date()
let img = MandalaRenderer.render(params: params)
let ms = Int(Date().timeIntervalSince(start) * 1000)
guard let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) else { exit(1) }
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(dest, cg, nil)
CGImageDestinationFinalize(dest)
print("rendered \(cg.width)x\(cg.height) in \(ms) ms, colorspace \(cg.colorSpace?.name as String? ?? "?")")
