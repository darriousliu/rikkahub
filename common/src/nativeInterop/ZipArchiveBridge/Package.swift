// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ZipArchiveBridge",
    platforms: [.iOS("15.0")],
    products: [.library(name: "ZipArchiveBridge", targets: ["ZipArchiveBridge"])],
    dependencies: [
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", exact: "0.9.20")
    ],
    targets: [
        .target(name: "ZipArchiveBridge", dependencies: ["ZIPFoundation"])
    ]
)
