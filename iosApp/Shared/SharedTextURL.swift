import Foundation

func sharedTextURL(_ text: String) -> URL {
    var components = URLComponents()
    components.scheme = "rikkahub"
    components.host = "share"
    components.queryItems = [URLQueryItem(name: "text", value: text)]
    return components.url!
}

func sharedText(from url: URL) -> String? {
    guard url.scheme == "rikkahub", url.host == "share" else { return nil }
    return URLComponents(url: url, resolvingAgainstBaseURL: false)?
        .queryItems?.first(where: { $0.name == "text" })?.value
}
