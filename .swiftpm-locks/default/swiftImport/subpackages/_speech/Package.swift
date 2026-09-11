// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_speech",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_speech",
      type: .none,
      targets: ["_speech"]
    )
  ],
  dependencies: [
  ],
  targets: [
    .target(
      name: "_speech",
      dependencies: [
      ]
    )
  ]
)
