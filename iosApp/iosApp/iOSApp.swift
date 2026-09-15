import RikkaHubShared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        CrashHandler.shared.install()
        KoinKt.doInitKoin { application in
            application.modules(modules_: [IosModuleKt.iosModule(
                pdfTextExtractor: PdfKitTextExtractor(), screenTimeProvider: ScreenTimeProvider()
            )])
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
