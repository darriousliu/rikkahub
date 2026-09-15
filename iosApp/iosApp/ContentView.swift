import RikkaHubShared
import SwiftUI

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(pdfTextExtractor: PdfKitTextExtractor())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                if let text = sharedText(from: url) {
                    MainViewControllerKt.receiveSharedText(text: text)
                }
            }
    }
}

#Preview {
    ContentView()
}
