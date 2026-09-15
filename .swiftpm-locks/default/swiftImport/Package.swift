// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(path: "subpackages/_ai"),
    .package(path: "subpackages/_common"),
    .package(path: "subpackages/_composeApp"),
    .package(path: "subpackages/_document"),
    .package(path: "subpackages/_search"),
    .package(path: "subpackages/_speech")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "_ai", package: "_ai"),
        .product(name: "_common", package: "_common"),
        .product(name: "_composeApp", package: "_composeApp"),
        .product(name: "_document", package: "_document"),
        .product(name: "_search", package: "_search"),
        .product(name: "_speech", package: "_speech")
      ]
    )
  ]
)
