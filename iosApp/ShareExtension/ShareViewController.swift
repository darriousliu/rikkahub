import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private var started = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let indicator = UIActivityIndicatorView(style: .large)
        indicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(indicator)
        NSLayoutConstraint.activate([
            indicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            indicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        indicator.startAnimating()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        Task { @MainActor in
            do {
                let text = try await receiveText()
                guard let scene = view.window?.windowScene else {
                    throw ShareError.cannotOpenApp
                }
                let opened = await scene.open(sharedTextURL(text), options: nil)
                guard opened else { throw ShareError.cannotOpenApp }
                extensionContext?.completeRequest(returningItems: nil)
            } catch {
                showError(error)
            }
        }
    }

    private func receiveText() async throws -> String {
        for item in extensionContext?.inputItems as? [NSExtensionItem] ?? [] {
            for provider in item.attachments ?? [] {
                for type in [UTType.url, UTType.plainText] where provider.hasItemConformingToTypeIdentifier(type.identifier) {
                    let value: NSSecureCoding? = try await withCheckedThrowingContinuation { continuation in
                        provider.loadItem(forTypeIdentifier: type.identifier, options: nil) { value, error in
                            if let error { continuation.resume(throwing: error) }
                            else { continuation.resume(returning: value) }
                        }
                    }
                    if let url = value as? URL { return url.absoluteString }
                    if let text = value as? String { return text }
                }
            }
            if let text = item.attributedContentText?.string { return text }
        }
        throw ShareError.noText
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(title: "RikkaHub", message: error.localizedDescription, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: NSLocalizedString("Close", comment: ""), style: .cancel) { [weak self] _ in
            self?.extensionContext?.cancelRequest(withError: error)
        })
        present(alert, animated: true)
    }
}

private enum ShareError: LocalizedError {
    case noText
    case cannotOpenApp

    var errorDescription: String? {
        switch self {
        case .noText: NSLocalizedString("No shared text or link was received.", comment: "")
        case .cannotOpenApp: NSLocalizedString("Unable to open RikkaHub from the share sheet.", comment: "")
        }
    }
}
