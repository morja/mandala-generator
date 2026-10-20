// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "MandalaGenerator",
    platforms: [
        .macOS(.v14)
    ],
    targets: [
        .executableTarget(
            name: "MandalaGenerator",
            path: "Sources/MandalaGenerator"
        )
    ]
)
