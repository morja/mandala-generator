import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Reference renderer: mandala-ref <settings.json> <out.png> [outputSize]
let args = CommandLine.arguments
guard args.count >= 3 else { print("usage: mandala-ref in.json out.png [size]"); exit(2) }
let data = try Data(contentsOf: URL(fileURLWithPath: args[1]))
var params = try JSONDecoder().decode(MandalaParameters.self, from: data)
params.resolvedPalettes = ColorPalettes.all
if args.count >= 4, let s = Int(args[3]) { params.outputSize = s }
let start = Date()
let img = MandalaRenderer.render(params: params)
let ms = Int(Date().timeIntervalSince(start) * 1000)
guard let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) else { exit(1) }
let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(dest, cg, nil)
CGImageDestinationFinalize(dest)
print("rendered \(cg.width)x\(cg.height) in \(ms) ms, colorspace \(cg.colorSpace?.name as String? ?? "?")")
