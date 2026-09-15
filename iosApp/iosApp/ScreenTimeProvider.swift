import DeviceActivity
import FamilyControls
import Foundation
import RikkaHubShared
import SwiftUI

final class ScreenTimeProvider: IosScreenTimeProvider {
    var permissionError: String? {
        guard #available(iOS 26.4, *) else {
            return failure("UNSUPPORTED_OS", "Screen Time data access requires iOS 26.4 or later.")
        }
        guard AuthorizationCenter.shared.authorizationStatus == .approvedWithDataAccess else {
            return failure("NO_PERMISSION", "Allow Screen Time app and website data access for RikkaHub in the assistant's local tools settings.")
        }
        return nil
    }

    func requestPermission(completion: @escaping (String?) -> Void) -> () -> Void {
        let task = Task { @MainActor in
            guard #available(iOS 26.4, *) else {
                completion(permissionError)
                return
            }
            do {
                try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                try Task.checkCancellation()
                if let error = permissionError {
                    completion(error)
                    return
                }
                do {
                    // Authorization alone does not guarantee that regional data access is available.
                    var data = DeviceActivityData.activityData(using: .cached).makeAsyncIterator()
                    _ = try await data.next()
                } catch DeviceActivityData.Error.missingData {
                    // Access is available, but this device has not recorded activity yet.
                }
                try Task.checkCancellation()
                completion(nil)
            } catch is CancellationError {
                return
            } catch DeviceActivityData.Error.unavailable {
                completion(failure("UNAVAILABLE", "Screen Time data export is unavailable on this device. Customer installations require a device in the EU and an EU Apple Account; development builds can be tested in other regions."))
            } catch {
                completion(failure("NO_PERMISSION", error.localizedDescription +
                    " Screen Time data access needs the Family Controls App and Website Usage entitlement and user authorization."))
            }
        }
        return { task.cancel() }
    }

    func query(startMillis: Double, endMillis: Double, completion: @escaping (String) -> Void) -> () -> Void {
        let task = Task { @MainActor in
            if let error = permissionError {
                completion(error)
                return
            }
            guard #available(iOS 26.4, *) else { return }
            do {
                let interval = DateInterval(start: Date(timeIntervalSince1970: startMillis / 1000),
                                            end: Date(timeIntervalSince1970: endMillis / 1000))
                // A nil device filter requests this device, not all devices in the family.
                let filter = DeviceActivityFilter(segment: .hourly(during: interval))
                var apps: [String: (name: String, milliseconds: Int64)] = [:]
                for try await data in DeviceActivityData.activityData(filteredBy: filter, using: .live) {
                    for await segment in data.activitySegments {
                        for await category in segment.categories {
                            for await item in category.applications {
                                try Task.checkCancellation()
                                guard let id = item.application.bundleIdentifier else {
                                    completion(failure("NO_PERMISSION", "Screen Time returned anonymized application data. Full app and website data access is required."))
                                    return
                                }
                                let duration = Int64(item.totalActivityDuration * 1000)
                                let previous = apps[id]?.milliseconds ?? 0
                                apps[id] = (item.application.localizedDisplayName ?? id, previous + duration)
                            }
                        }
                    }
                }
                try Task.checkCancellation()
                completion(json(["apps": apps.map { id, value in
                    ["id": id, "name": value.name, "milliseconds": value.milliseconds] as [String: Any]
                }]))
            } catch is CancellationError {
                return
            } catch DeviceActivityData.Error.unavailable {
                completion(failure("UNAVAILABLE", "Screen Time data export is unavailable on this device. Customer installations require a device in the EU and an EU Apple Account; development builds can be tested in other regions."))
            } catch DeviceActivityData.Error.unauthorized {
                completion(failure("NO_PERMISSION", "Screen Time data authorization is missing or revoked. Allow app and website data access for RikkaHub in Settings."))
            } catch DeviceActivityData.Error.missingData {
                completion(failure("NO_DATA", "Screen Time has no usage data for this interval. Check that App & Website Activity is enabled in Settings."))
            } catch {
                completion(failure("SCREEN_TIME_FAILED", error.localizedDescription))
            }
        }
        return { task.cancel() }
    }

    private func failure(_ code: String, _ message: String) -> String {
        json(["error": code, "message": message])
    }

    private func json(_ value: [String: Any]) -> String {
        // Values here are exclusively JSON strings, numbers, arrays and dictionaries.
        String(data: try! JSONSerialization.data(withJSONObject: value), encoding: .utf8)!
    }
}
