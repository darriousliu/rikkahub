import Foundation
import SwiftSoup
import ComposeApp

class HtmlEscapeUtil: HtmlEscaper {
    static let shared = HtmlEscapeUtil()

    func escapeHtml(input: String) -> String {
        return Entities.escape(input)
    }

    func unescapeHtml(input: String) -> String {
        do {
            return try Entities.unescape(input)
        } catch {
            // If unescaping fails, return the original input
            return input
        }
    }
}
