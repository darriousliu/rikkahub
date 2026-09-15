// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_document",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_document",
      type: .none,
      targets: ["_document"]
    )
  ],
  dependencies: [
  ],
  targets: [
    .target(
      name: "_document",
      dependencies: [
      ]
    )
  ]
)
