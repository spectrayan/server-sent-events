// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "SpectrayanSSE",
    platforms: [
        .iOS(.v15),
        .macOS(.v12),
        .watchOS(.v8),
        .tvOS(.v15),
        .visionOS(.v1)
    ],
    products: [
        .library(
            name: "SpectrayanSSE",
            targets: ["SpectrayanSSE"]
        )
    ],
    targets: [
        .target(
            name: "SpectrayanSSE"
        ),
        .testTarget(
            name: "SpectrayanSSETests",
            dependencies: ["SpectrayanSSE"]
        )
    ],
    swiftLanguageModes: [.v6]
)