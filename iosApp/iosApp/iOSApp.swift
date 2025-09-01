import SwiftUI
import Firebase
import ComposeApp

@main
struct iOSApp: App {
    // 创建通知代理
    @StateObject private var notificationDelegate = NotificationDelegate()

    init() {
        FirebaseApp.configure()
        AppInitializer.shared.doInitKoin { app in
            app.doInitIOSKoin(di: [
                HtmlEscapeUtil.shared,
                QRCodeUtilProvider.shared,
                IosQRCodeDecoder.shared,
                DocumentUtil.shared,
                IosQRCodeEncoder.shared,
            ])
        }
        AppInitializer.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
            .onAppear {
                setupNotifications()
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("NavigateToConversation"))) { notification in
                // 处理导航到会话
                if let conversationId = notification.userInfo?["conversationId"] as? String {
                    NavigationController.shared.navigateToChat(conversationId: conversationId)
                }
            }
        }
    }

    private func setupNotifications() {
        // 设置通知代理
        UNUserNotificationCenter.current().delegate = notificationDelegate
    }
}

// 单独的通知代理类
class NotificationDelegate: NSObject, ObservableObject, UNUserNotificationCenterDelegate {

    // 处理通知点击
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {

        let userInfo = response.notification.request.content.userInfo
        if let conversationId = userInfo["conversationId"] as? String {
            // 通过 NotificationCenter 发送导航事件
            NotificationCenter.default.post(
                name: NSNotification.Name("NavigateToConversation"),
                object: nil,
                userInfo: ["conversationId": conversationId]
            )
        }

        completionHandler()
    }

    // 前台显示通知
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // 在前台也显示通知
        completionHandler([.banner, .sound])
    }
}
