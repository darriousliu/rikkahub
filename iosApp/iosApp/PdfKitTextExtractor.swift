import PDFKit
import RikkaHubShared

final class PdfKitTextExtractor: PdfTextExtractor {
    func extract(url: URL) throws -> String {
        guard let document = PDFDocument(url: url), !document.isLocked else {
            throw NSError(domain: "PdfKitTextExtractor", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Failed to open PDF document"
            ])
        }
        var result = ""
        for i in 0..<document.pageCount {
            try autoreleasepool {
                guard let page = document.page(at: i) else {
                    throw NSError(domain: "PdfKitTextExtractor", code: 2, userInfo: [
                        NSLocalizedDescriptionKey: "Failed to load PDF page \(i + 1)"
                    ])
                }
                result += "---Page \(i + 1):\n"
                result += page.string ?? ""
                result += "\n"
            }
        }
        return result
    }
}
