import Foundation
import ZIPFoundation

@objc(RKHZipArchive)
public final class ZipArchiveBridge: NSObject {
    private var archive: Archive?
    private let url: URL
    private var entries: AnyIterator<Entry>?
    private var currentEntry: Entry?

    private init(archive: Archive, url: URL, writing: Bool) {
        self.archive = archive
        self.url = url
        self.entries = writing ? nil : archive.makeIterator()
        super.init()
    }

    @objc(openAtPath:writing:error:)
    public static func open(atPath path: String, writing: Bool) throws -> ZipArchiveBridge {
        let url = URL(fileURLWithPath: path)
        let archive = try Archive(url: url, accessMode: writing ? .create : .read)
        return ZipArchiveBridge(archive: archive, url: url, writing: writing)
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

    @objc(openEntryNamed:error:)
    public func openEntry(named name: String, error: NSErrorPointer) -> ZipEntrySourceBridge? {
        do {
            guard self.archive != nil else { throw CocoaError(.fileReadUnknown) }
            // Each source owns its file handle, so another reader cannot move its file position.
            let archive = try Archive(url: url, accessMode: .read)
            guard let entry = archive[name] else { return nil }
            return ZipEntrySourceBridge(archive: archive, entry: entry)
        } catch let failure {
            error?.pointee = failure as NSError
            return nil
        }
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

/// Converts ZIPFoundation's push consumer to a pull source with bounded buffering.
@objc(RKHZipEntrySource)
public final class ZipEntrySourceBridge: NSObject {
    private let condition = NSCondition()
    private let completion = DispatchGroup()
    private var chunk: Data?
    private var offset = 0
    private var finished = false
    private var closed = false
    private var failure: Error?

    fileprivate init(archive: Archive, entry: Entry) {
        super.init()
        completion.enter()
        DispatchQueue.global(qos: .utility).async {
            defer { self.completion.leave() }
            var failure: Error?
            do {
                let checksum = try archive.extract(entry, bufferSize: 64 * 1024) { data in
                    if data.isEmpty { return }
                    self.condition.lock()
                    defer { self.condition.unlock() }
                    while self.chunk != nil && !self.closed { self.condition.wait() }
                    guard !self.closed else { throw Archive.ArchiveError.cancelledOperation }
                    self.chunk = data
                    self.offset = 0
                    self.condition.broadcast()
                }
                guard checksum == entry.checksum else { throw Archive.ArchiveError.invalidCRC32 }
            } catch {
                failure = error
            }
            self.condition.lock()
            self.failure = failure
            self.finished = true
            self.condition.broadcast()
            self.condition.unlock()
        }
    }

    @objc(readInto:count:error:)
    public func read(into buffer: UnsafeMutableRawPointer, count: Int, error: NSErrorPointer) -> Int {
        condition.lock()
        defer { condition.unlock() }
        while chunk == nil && !finished && !closed { condition.wait() }
        guard !closed else {
            error?.pointee = CocoaError(.fileReadUnknown) as NSError
            return -1
        }
        if let chunk = chunk {
            let length = min(count, chunk.count - offset)
            chunk.withUnsafeBytes { bytes in
                buffer.copyMemory(from: bytes.baseAddress!.advanced(by: offset), byteCount: length)
            }
            offset += length
            if offset == chunk.count {
                self.chunk = nil
                condition.broadcast()
            }
            return length
        }
        if let failure = failure { error?.pointee = failure as NSError }
        return -1
    }

    @objc public func close() {
        condition.lock()
        closed = true
        chunk = nil
        condition.broadcast()
        condition.unlock()
        // Unblocks a producer waiting for the consumer and waits for extraction to stop.
        completion.wait()
    }

    deinit { close() }
}
