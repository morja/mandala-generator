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
        // Lift faint linework for a 24-point icon while keeping the background black.
        // This changes only the picker artwork, never a saved mandala or the renderer.
        guard let pixels = context.data?.assumingMemoryBound(to: UInt8.self) else { exit(1) }
        var peak: UInt8 = 0
        for y in 0..<96 {
            for x in 0..<96 {
                let offset = y * context.bytesPerRow + x * 4
                for channel in 0..<3 {
                    let value = pixels[offset + channel]
                    peak = max(peak, value)
                    pixels[offset + channel] = UInt8((sqrt(Double(value) / 255) * 255).rounded())
                }
            }
        }
        guard peak > 0 else { print("Empty style preview: \(style.rawValue)"); exit(1) }
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
