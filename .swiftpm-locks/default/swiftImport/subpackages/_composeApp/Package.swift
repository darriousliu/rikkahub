// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_composeApp",
  platforms: [
    .iOS("15.0")
  ],
  products: [
    .library(
      name: "_composeApp",
      type: .none,
      targets: ["_composeApp"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/firebase/firebase-ios-sdk.git",
      exact: "12.17.0"
    )
  ],
  targets: [
    .target(
      name: "_composeApp",
      dependencies: [
        .product(
          name: "FirebaseAnalytics",
          package: "firebase-ios-sdk"
        ),
        .product(
          name: "FirebaseCrashlytics",
          package: "firebase-ios-sdk"
        )
      ]
    )
  ]
)
