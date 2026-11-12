// Draws the local-build mark (a dark ring with an amber dot, bottom right) onto an app icon, the same
// mark as Android's ic_launcher_foreground_marked.xml. Android's mark is a quarter of the visible icon;
// here it keeps its size relative to Android's whole 108 dp layer, so it stays small on the full-bleed icon.
// Usage: swift ios/Dahlia/scripts/mark-icon.swift <in.png> <out.png>
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let args = CommandLine.arguments
guard args.count == 3,
      let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    FileHandle.standardError.write("usage: mark-icon.swift <in.png> <out.png>\n".data(using: .utf8)!)
    exit(2)
}
let size = image.width
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))

// Android: ring radius 8, dot 5.5 of 108; the centre sits where Android's does in the visible icon (52 of 66).
let unit = Double(size) / 108.0
let centre = Double(size) * 52.0 / 66.0
let y = Double(size) - centre // Core Graphics counts from the bottom
func disc(_ radius: Double, _ r: Double, _ g: Double, _ b: Double) {
    ctx.setFillColor(red: r, green: g, blue: b, alpha: 1)
    ctx.fillEllipse(in: CGRect(x: centre - radius * unit, y: y - radius * unit, width: 2 * radius * unit, height: 2 * radius * unit))
}
disc(8.0, 0x15 / 255.0, 0x14 / 255.0, 0x13 / 255.0)
disc(5.5, 0xE2 / 255.0, 0xA8 / 255.0, 0x5E / 255.0)

let out = CGImageDestinationCreateWithURL(URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(out, ctx.makeImage()!, nil)
CGImageDestinationFinalize(out)
