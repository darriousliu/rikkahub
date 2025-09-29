import Foundation
import UIKit
import SwiftQRCodeScanner
import ComposeApp
import QRCode

class QRCodeUtil: QRCodeScanner {
    let onResult: (String) -> Void

    init(onResult: @escaping (String) -> Void) {
        self.onResult = onResult
    }

    func startScanning() {
        DispatchQueue.main.async {
            guard let topViewController = self.getTopViewController() else {
                print("无法获取当前视图控制器")
                return
            }

            self.presentQRScanner(from: topViewController)
        }
    }

    private func presentQRScanner(from viewController: UIViewController) {
        var configuration = QRScannerConfiguration()
        configuration.title = ""
        configuration.cameraImage = UIImage(named: "camera")
        configuration.flashOnImage = UIImage(named: "flash-on")
        configuration.galleryImage = UIImage(named: "photos")
        configuration.readQRFromPhotos = false
        // 使用简单配置的扫描器
        let scanner = QRCodeScannerController(qrScannerConfiguration: configuration)
        scanner.delegate = self
        viewController.present(scanner, animated: true, completion: nil)
    }

    private func getTopViewController() -> UIViewController? {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first
        else {
            return nil
        }

        var topViewController = window.rootViewController
        while let presentedViewController = topViewController?.presentedViewController {
            topViewController = presentedViewController
        }

        return topViewController
    }
}

extension QRCodeUtil: QRScannerCodeDelegate {
    func qrScanner(_ controller: UIViewController, didScanQRCodeWithResult result: String) {
        onResult(result)
    }

    func qrScanner(_ controller: UIViewController, didFailWithError error: SwiftQRCodeScanner.QRCodeError) {
        print("error:\(error.localizedDescription)")
    }

    func qrScannerDidCancel(_ controller: UIViewController) {
        print("SwiftQRScanner did cancel")
    }
}

class QRCodeUtilProvider: ProviderQRCodeScanner {
    static let shared = QRCodeUtilProvider()

    func factory(onResult: @escaping (String) -> Void) -> QRCodeScanner {
        return QRCodeUtil(onResult: onResult)
    }
}

class IosQRCodeDecoder: QRCodeDecoder {
    static let shared = IosQRCodeDecoder()

    func decode(uri: String) -> String? {
        // 从URI创建UIImage
        guard let url = URL(string: uri) else {
            return nil
        }

        var image: UIImage?

        if url.isFileURL {
            // 本地文件
            image = UIImage(contentsOfFile: url.path)
        } else {
            // 网络图片或其他情况，尝试同步加载数据
            do {
                let imageData = try Data(contentsOf: url)
                image = UIImage(data: imageData)
            } catch {
                print("Failed to load image from URL: \(error)")
                return nil
            }
        }

        guard let uiImage = image else {
            return nil
        }

        // 使用Vision框架解析QR码
        return uiImage.parseQRCode()
    }
}

extension UIImage {

    /// Parses a QR code from the current image and returns its payload as a `String`.
    ///
    /// This method uses CoreImage to detect and decode any QR codes present in the image. If multiple QR codes are found, it returns the payload of the first one.
    ///
    /// - Returns: The payload of the first detected QR code as a `String`, or `nil` if no QR code is found or an error occurs during the detection process.
    func parseQRCode() -> String? {
        // Create a CoreImage representation of the UIImage
        guard let ciImage = CIImage(image: self) else {
            return nil
        }

        // Set options for the QR code detector
        let detectorOptions = [CIDetectorAccuracy: CIDetectorAccuracyHigh]

        // Create a QR code detector with the specified options
        guard let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil, options: detectorOptions),
              // Use the detector to find QR code features in the image
              let features = detector.features(in: ciImage) as? [CIQRCodeFeature],
              // Extract the payload of the first detected QR code
              let payload = features.compactMap({ $0.messageString }).first
        else {
            return nil
        }

        // Return the payload of the first detected QR code
        return payload
    }
}

class IosQRCodeEncoder: QRCodeEncoder {
    static let shared = IosQRCodeEncoder()

    func encode(data: String, size: Int32, color: String, backgroundColor: String) -> UIImage {
        do {
            let cgImage = try QRCode.build
                .text(data)
                .foregroundColor(hexString: color)
                .backgroundColor(hexString: backgroundColor)
                .generate.image(dimension: Int(size))
            return UIImage(cgImage: cgImage)
        } catch {
            print("Failed to encode QR code: \(error)")
            // Return a fallback image on failure - a simple 1x1 transparent image
            return UIImage()
        }
    }
}
