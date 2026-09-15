import Foundation
import ZIPFoundation

@objc(RKHZipArchive)
public final class ZipArchiveBridge: NSObject {
    private var archive: Archive?
    private var entries: AnyIterator<Entry>?
    private var currentEntry: Entry?

    private init(archive: Archive, writing: Bool) {
        self.archive = archive
        self.entries = writing ? nil : archive.makeIterator()
        super.init()
    }

    @objc(openAtPath:writing:error:)
    public static func open(atPath path: String, writing: Bool) throws -> ZipArchiveBridge {
        let archive = try Archive(url: URL(fileURLWithPath: path), accessMode: writing ? .create : .read)
        return ZipArchiveBridge(archive: archive, writing: writing)
    }

    @objc(addFileNamed:fromPath:error:)
    public func addFile(named name: String, fromPath path: String) throws {
        try archive!.addEntry(with: name, fileURL: URL(fileURLWithPath: path),
                              compressionMethod: .deflate, bufferSize: 64 * 1024)
    }

    @objc(addDirectoryNamed:error:)
    public func addDirectory(named name: String) throws {
        try archive!.addEntry(with: name, type: .directory, uncompressedSize: Int64(0)) { _, _ in Data() }
    }

    @objc public func nextEntry() -> Bool {
        currentEntry = entries?.next()
        return currentEntry != nil
    }

    @objc public var entryName: String { currentEntry!.path }
    @objc public var entryIsDirectory: Bool { currentEntry!.type == .directory }

    @objc(selectEntryNamed:)
    public func selectEntry(named name: String) -> Bool {
        currentEntry = archive?[name]
        return currentEntry != nil
    }

    @objc(readCurrentEntryWithConsumer:error:)
    public func readCurrentEntry(consumer: (UnsafeRawPointer?, Int) -> Bool) throws {
        let entry = currentEntry!
        let checksum = try archive!.extract(entry, bufferSize: 64 * 1024) { data in
            let accepted = data.withUnsafeBytes { consumer($0.baseAddress, $0.count) }
            guard accepted else { throw Archive.ArchiveError.cancelledOperation }
        }
        guard checksum == entry.checksum else { throw Archive.ArchiveError.invalidCRC32 }
    }

    @objc public func close() {
        entries = nil
        currentEntry = nil
        archive = nil
    }
}
