// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_ai",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_ai",
      type: .none,
      targets: ["_ai"]
    )
  ],
  dependencies: [
  ],
  targets: [
    .target(
      name: "_ai",
      dependencies: [
      ]
    )
  ]
)
