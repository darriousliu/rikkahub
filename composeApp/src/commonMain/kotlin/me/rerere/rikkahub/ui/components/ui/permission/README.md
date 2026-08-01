# 权限组件使用说明

权限组件由共享 UI 契约和平台实现组成：

- `commonMain` 提供 `PermissionInfo`、`PermissionState`、`PermissionManager` 和权限说明对话框。
- `androidMain` 使用 Activity Result API 请求权限，并在生命周期恢复时刷新状态。
- iOS/JVM 的共享状态不拦截功能入口；原生能力自行处理授权，页面仍须通过 `CapabilityGate` 判断能力是否可用。

## 基本使用

```kotlin
@Composable
fun CameraContent() {
    val permissionState = rememberPermissionState(PermissionCamera)

    PermissionManager(permissionState)

    Button(
        onClick = permissionState::requestPermissions,
        enabled = !permissionState.allRequiredPermissionsGranted,
    ) {
        Text("Request camera permission")
    }
}
```

自定义 Android 权限仍可使用通用模型：

```kotlin
val permissionState = rememberPermissionState(
    permissions = setOf(
        PermissionInfo(
            permission = Manifest.permission.READ_CALENDAR,
            displayName = { Text("Calendar") },
            usage = { Text("Read calendar events") },
            required = true,
        )
    )
)
```

## 约束

- 只在实际使用能力前请求权限。
- Android 权限必须同时声明在 manifest 中。
- 共享页面先检查对应 `PlatformCapability`；权限状态不能代替平台能力判断。
- 永久拒绝后由 `openAppSettings()` 打开 Android 应用设置。
