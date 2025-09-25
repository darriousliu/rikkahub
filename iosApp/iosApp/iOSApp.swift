import SwiftUI
import Firebase
import ComposeApp

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        AppInitializer.shared.doInitKoin { _ in
        }
        AppInitializer.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
