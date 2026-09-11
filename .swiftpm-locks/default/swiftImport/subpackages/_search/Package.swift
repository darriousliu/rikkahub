// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_search",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_search",
      type: .none,
      targets: ["_search"]
    )
  ],
  dependencies: [
  ],
  targets: [
    .target(
      name: "_search",
      dependencies: [
      ]
    )
  ]
)
