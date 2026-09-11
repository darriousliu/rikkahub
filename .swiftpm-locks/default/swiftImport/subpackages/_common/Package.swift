// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_common",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_common",
      type: .none,
      targets: ["_common"]
    )
  ],
  dependencies: [
    .package(
      path: "../../../../../common/src/nativeInterop/ZipArchiveBridge"
    )
  ],
  targets: [
    .target(
      name: "_common",
      dependencies: [
        .product(
          name: "ZipArchiveBridge",
          package: "ZipArchiveBridge"
        )
      ]
    )
  ]
)
