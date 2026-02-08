import Foundation
import ComposeApp
import ZIPFoundation

class IosZipUtil: NSObject, ZipUtil {

    static let shared = IosZipUtil()

    func compress(sourcePath: String, destinationPath: String) -> Bool {
        let sourceURL = getValidURL(path: sourcePath)
        let destinationURL = getValidURL(path: destinationPath)
        do {
            try FileManager.default.zipItem(at: sourceURL, to: destinationURL)
            return true
        } catch {
            print("compress error: \(error)")
            return false
        }
    }

    func compressMultiple(sourcePaths: [String], destinationPath: String) -> Bool {
        let destinationURL = getValidURL(path: destinationPath)
        do {
            let archive = try Archive(url: destinationURL, accessMode: .create)
            for sourcePath in sourcePaths {
                let sourceURL = getValidURL(path: sourcePath)
                let fileName = sourceURL.lastPathComponent
                try archive.addEntry(with: fileName, relativeTo: sourceURL.deletingLastPathComponent())
            }
            return true
        } catch {
            print("compress multiple files error: \(error)")
            return false
        }
    }

    func getZipEntryContent(zipFilePath: String, entryName: String) -> KotlinByteArray? {
        let sourceURL = getValidURL(path: zipFilePath)
        guard let archive = try? Archive(url: sourceURL, accessMode: .read),
              let entry = archive[entryName]
        else {
            return nil
        }

        var data = Data()
        do {
            _ = try archive.extract(entry) {
                data.append($0)
            }
        } catch {
            print("extract error: \(error)")
            return nil
        }

        let byteArray = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            byteArray.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return byteArray
    }

    func getZipEntryList(zipFilePath: String) -> [KotlinPair<NSString, KotlinBoolean>] {
        let sourceURL = getValidURL(path: zipFilePath)
        guard let archive = try? Archive(url: sourceURL, accessMode: .read) else {
            return []
        }

        var list: [KotlinPair<NSString, KotlinBoolean>] = []
        for entry in archive {
            let path = entry.path as NSString
            let isDir = entry.type == .directory
            let pair = KotlinPair(first: path, second: KotlinBoolean(bool: isDir))
            list.append(pair)
        }
        return list
    }

    func unzip(sourcePath: String, destinationPath: String) -> Bool {
        let sourceURL = getValidURL(path: sourcePath)
        let destinationURL = getValidURL(path: destinationPath)
        do {
            try FileManager.default.createDirectory(at: destinationURL, withIntermediateDirectories: true, attributes: nil)
            try FileManager.default.unzipItem(at: sourceURL, to: destinationURL)
            return true
        } catch {
            print("unzip error: \(error)")
            return false
        }
    }

    func createZipFromEntries(
            entries: [String: KotlinByteArray],
            destinationPath: String
        ) -> Bool {
            let destinationURL = getValidURL(path: destinationPath)
            // 如果文件已存在则先删除
            if FileManager.default.fileExists(atPath: destinationURL.path) {
                try? FileManager.default.removeItem(at: destinationURL)
            }
            guard let archive = Archive(url: destinationURL, accessMode: .create) else {
                print("createZipFromEntries: Failed to create archive at \(destinationPath)")
                return false
            }
            do {
                for (entryName, kotlinByteArray) in entries {
                    let size = Int(kotlinByteArray.size)
                    var data = Data(count: size)
                    for i in 0..<size {
                        data[i] = UInt8(bitPattern: kotlinByteArray.get(index: Int32(i)))
                    }
                    try archive.addEntry(
                        with: entryName,
                        type: .file,
                        uncompressedSize: Int64(size),
                        provider: { (position: Int64, bufferSize: Int) -> Data in
                            let start = Int(position)
                            let end = min(start + bufferSize, size)
                            return data.subdata(in: start..<end)
                        }
                    )
                }
                return true
            } catch {
                print("createZipFromEntries error: \(error)")
                return false
            }
        }
    
    func createZipWriter(destinationPath: String) -> any ZipWriter {
        return IosZipWriter(destinationPath: destinationPath)!
    }

    func extractEntryToFile(zipFilePath: String, entryName: String, targetFilePath: String) -> Bool {
        return true
    }

    private func getValidURL(path: String) -> URL {
        return if path.hasPrefix("file://") {
            URL(string: path)!
        } else {
            URL(fileURLWithPath: path)
        }
    }
}

class IosZipWriter: NSObject, ZipWriter {
    private let archive: Archive
    init?(destinationPath: String) {
        let url: URL
        if destinationPath.hasPrefix("file://") {
            url = URL(string: destinationPath)!
        } else {
            url = URL(fileURLWithPath: destinationPath)
        }
        // 已存在则先删除
        if FileManager.default.fileExists(atPath: url.path) {
            try? FileManager.default.removeItem(at: url)
        }
        guard let archive = Archive(url: url, accessMode: .create) else {
            return nil
        }
        self.archive = archive
        super.init()
    }
    func addEntry(entryName: String, data: KotlinByteArray) {
        let size = Int(data.size)
        var swiftData = Data(count: size)
        for i in 0..<size {
            swiftData[i] = UInt8(bitPattern: data.get(index: Int32(i)))
        }
        do {
            try archive.addEntry(
                with: entryName,
                type: .file,
                uncompressedSize: Int64(size),
                provider: { (position: Int64, bufferSize: Int) -> Data in
                    let start = Int(position)
                    let end = min(start + bufferSize, size)
                    return swiftData.subdata(in: start..<end)
                }
            )
        } catch {
            print("IosZipWriter addEntry error: \(error)")
        }
    }
    func addEntryFromFile(entryName: String, filePath: String) {
        let fileURL: URL
        if filePath.hasPrefix("file://") {
            fileURL = URL(string: filePath)!
        } else {
            fileURL = URL(fileURLWithPath: filePath)
        }
        guard let fileData = FileManager.default.contents(atPath: fileURL.path) else {
            print("IosZipWriter addEntryFromFile: cannot read \(filePath)")
            return
        }
        // 使用流式 provider，按块提供数据
        let totalSize = fileData.count
        do {
            try archive.addEntry(
                with: entryName,
                type: .file,
                uncompressedSize: Int64(totalSize),
                provider: { (position: Int64, bufferSize: Int) -> Data in
                    let start = Int(position)
                    let end = min(start + bufferSize, totalSize)
                    return fileData.subdata(in: start..<end)
                }
            )
        } catch {
            print("IosZipWriter addEntryFromFile error: \(error)")
        }
    }
    func close() {
        // ZIPFoundation 的 Archive 在 create 模式下
        // 写入操作已经在 addEntry 时完成，无需额外 close
        // Archive 会在 deinit 时释放资源
    }
}
