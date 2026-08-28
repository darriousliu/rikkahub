package me.rerere.rikkahub.ui.components.ui.permission

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.permission_camera
import me.rerere.rikkahub.generated.resources.permission_camera_desc
import me.rerere.rikkahub.generated.resources.permission_local_network
import me.rerere.rikkahub.generated.resources.permission_local_network_desc
import me.rerere.rikkahub.generated.resources.permission_microphone
import me.rerere.rikkahub.generated.resources.permission_microphone_desc
import me.rerere.rikkahub.generated.resources.permission_notification
import me.rerere.rikkahub.generated.resources.permission_notification_desc
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

private const val NOTIFICATION_PERMISSION = "notifications"
private const val LOCAL_NETWORK_PERMISSION = "local_network"

actual val PermissionCamera: PermissionInfo = PermissionInfo(
    permission = MediaPermission.Camera.id,
    displayName = { Text(stringResource(Res.string.permission_camera)) },
    usage = { Text(stringResource(Res.string.permission_camera_desc)) },
    required = true,
)

actual val PermissionRecordAudio: PermissionInfo = PermissionInfo(
    permission = MediaPermission.Microphone.id,
    displayName = { Text(stringResource(Res.string.permission_microphone)) },
    usage = { Text(stringResource(Res.string.permission_microphone_desc)) },
    required = true,
)

actual val PermissionNotification: PermissionInfo = PermissionInfo(
    permission = NOTIFICATION_PERMISSION,
    displayName = { Text(stringResource(Res.string.permission_notification)) },
    usage = { Text(stringResource(Res.string.permission_notification_desc)) },
    required = true,
)

actual val RuntimeNotificationPermissionRequired: Boolean = false

actual val PermissionLocalNetwork: PermissionInfo = PermissionInfo(
    permission = LOCAL_NETWORK_PERMISSION,
    displayName = { Text(stringResource(Res.string.permission_local_network)) },
    usage = { Text(stringResource(Res.string.permission_local_network_desc)) },
    required = true,
)

actual val RuntimeLocalNetworkPermissionRequired: Boolean = false

@Composable
actual fun rememberPermissionState(permissions: Set<PermissionInfo>): PermissionState {
    val scope = rememberCoroutineScope()
    val state = remember(permissions) { IosPermissionState(permissions, scope) }
    // The user can flip a permission in Settings while we are backgrounded.
    DisposableEffect(state) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { state.refresh() },
        )
        onDispose { center.removeObserver(observer) }
    }
    return state
}

/**
 * Media capture is the only permission family iOS lets us query and request directly.
 * Notifications and local network stay passive: iOS asks for them on first use.
 */
private enum class MediaPermission(val id: String) {
    Camera("camera"),
    Microphone("record_audio");

    fun status(): PermissionStatus {
        val status = when (this) {
            Camera -> AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            Microphone -> AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
        }
        return when (status) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotRequested
            // Denied and Restricted are both terminal: iOS never prompts twice.
            else -> PermissionStatus.DeniedPermanently
        }
    }

    suspend fun requestAccess(): Unit = suspendCancellableCoroutine { continuation ->
        val completion: (Boolean) -> Unit = {
            if (continuation.isActive) continuation.resume(Unit)
        }
        when (this) {
            Camera -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo, completion)
            Microphone -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio, completion)
        }
    }
}

private fun PermissionInfo.mediaPermission(): MediaPermission? =
    MediaPermission.entries.firstOrNull { it.id == permission }

@Stable
private class IosPermissionState(
    permissions: Set<PermissionInfo>,
    private val scope: CoroutineScope,
) : PermissionState {
    private val managed: List<Pair<PermissionInfo, MediaPermission>> =
        permissions.mapNotNull { info -> info.mediaPermission()?.let { info to it } }

    private var statuses by mutableStateOf(readStatuses())

    override var showRationaleDialog by mutableStateOf(false)
        private set

    override var currentRationalePermissions by mutableStateOf<List<PermissionInfo>>(emptyList())
        private set

    override val permanentlyDeniedPermissions: List<PermissionInfo>
        get() = managed.filter { (info, _) -> statuses[info.permission] == PermissionStatus.DeniedPermanently }
            .map { (info, _) -> info }

    // Permissions iOS does not gate stay granted, which keeps the previous behaviour.
    override val allPermissionsGranted: Boolean
        get() = managed.all { (info, _) -> statuses[info.permission] == PermissionStatus.Granted }

    override val allRequiredPermissionsGranted: Boolean
        get() = managed.filter { (info, _) -> info.required }
            .all { (info, _) -> statuses[info.permission] == PermissionStatus.Granted }

    fun refresh() {
        statuses = readStatuses()
    }

    override fun requestPermissions() {
        refresh()
        val pending = managed.filter { (info, _) -> statuses[info.permission] != PermissionStatus.Granted }
        if (pending.isEmpty()) return
        val blocked = pending.filter { (info, _) -> statuses[info.permission] == PermissionStatus.DeniedPermanently }
        if (blocked.isNotEmpty()) {
            // Only Settings can undo a denial, so explain that instead of calling a prompt that never shows.
            currentRationalePermissions = blocked.map { (info, _) -> info }
            showRationaleDialog = true
            return
        }
        requestAccess(pending.map { (_, media) -> media })
    }

    /**
     * The rationale dialog routes its confirm button here, including the "go to settings" variant,
     * so this has to branch on permanent denial the same way the Android state does.
     */
    override fun proceedFromRationale() {
        showRationaleDialog = false
        val requested = currentRationalePermissions
        currentRationalePermissions = emptyList()
        if (requested.any { statuses[it.permission] == PermissionStatus.DeniedPermanently }) {
            openAppSettings()
        } else {
            requestAccess(requested.mapNotNull { it.mediaPermission() })
        }
    }

    override fun cancelPermissionRequest() {
        showRationaleDialog = false
        currentRationalePermissions = emptyList()
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        // The deprecated synchronous openURL: does not reliably open app-settings: on current iOS.
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }

    private fun requestAccess(media: List<MediaPermission>) {
        if (media.isEmpty()) return
        scope.launch {
            media.forEach { it.requestAccess() }
            refresh()
        }
    }

    private fun readStatuses(): Map<String, PermissionStatus> =
        managed.associate { (info, media) -> info.permission to media.status() }
}
