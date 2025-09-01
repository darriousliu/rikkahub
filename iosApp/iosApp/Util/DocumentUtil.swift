import Foundation
import ComposeApp
import PDFKit
import ZIPFoundation

class DocumentUtil: DocumentReader {
    static let shared = DocumentUtil()

    func extractDocxText(filePath: String) -> String? {
        do {
            guard let url = URL(string: filePath) else {
                print("filePath invalid")
                return nil
            }
            // Use the throwing initializer instead of the deprecated one
            let archive = try Archive(url: url, accessMode: .read)

            guard let documentEntry = archive["word/document.xml"] else {
                return nil
            }

            var xmlData = Data()
            try archive.extract(documentEntry) { data in
                xmlData.append(data)
            }

            guard let xmlString = String(data: xmlData, encoding: .utf8) else {
                return nil
            }

            let text = self.parseXMLContent(xmlString)
            return text

        } catch {
            return nil
        }
    }

    func extractPdfText(filePath: String) -> String? {
        // Fix: Use URL(fileURLWithPath:) for file paths instead of URL(string:)
        guard let url = URL(string: filePath) else {
            print("filePath invalid")
            return nil
        }

        guard let pdfDocument = PDFDocument(url: url) else {
            print("Document is empty: \(filePath)")
            return nil
        }

        var extractedText = ""
        let pageCount = pdfDocument.pageCount

        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex) else {
                continue
            }

            if let pageText = page.string {
                extractedText += pageText + "\n"
            }
        }

        return extractedText.isEmpty ? nil : extractedText
    }

    private func parseXMLContent(_ xml: String) -> String {
        // 提取 <w:t> 标签中的文本
        let pattern = "<w:t[^>]*>(.*?)</w:t>"
        let regex = try! NSRegularExpression(pattern: pattern, options: [])
        let range = NSRange(location: 0, length: xml.utf16.count)
        let matches = regex.matches(in: xml, options: [], range: range)

        var extractedText = ""
        for match in matches {
            if let range = Range(match.range(at: 1), in: xml) {
                extractedText += String(xml[range]) + " "
            }
        }

        return extractedText
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&amp;", with: "&")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
