import SwiftUI
import Firebase
import ComposeApp

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        AppInitializer.shared.doInitKoin { app in
            app.doInitIOSKoin(di: [
                HtmlEscapeUtil.shared,
                QRCodeUtilProvider.shared,
                IosQRCodeDecoder.shared
            ])
        }
        AppInitializer.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
