import RikkaHubShared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        CrashHandler.shared.install()
        KoinKt.doInitKoin { application in
            application.modules(modules_: [IosModuleKt.iosModule(pdfTextExtractor: PdfKitTextExtractor())])
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
