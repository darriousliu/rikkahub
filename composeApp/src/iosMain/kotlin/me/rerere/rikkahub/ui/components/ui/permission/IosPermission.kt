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

private const val CAMERA_PERMISSION = "camera"
private const val RECORD_AUDIO_PERMISSION = "record_audio"

actual val PermissionCamera: PermissionInfo = PermissionInfo(
    permission = CAMERA_PERMISSION,
    displayName = { Text("Camera") },
    usage = { Text("Used to scan QR codes") },
    required = true,
)

actual val PermissionRecordAudio: PermissionInfo = PermissionInfo(
    permission = RECORD_AUDIO_PERMISSION,
    displayName = { Text("Microphone") },
    usage = { Text("Used to record voice input") },
    required = true,
)

actual val PermissionNotification: PermissionInfo = passivePermission("notifications", "Notifications")
actual val RuntimeNotificationPermissionRequired: Boolean = false
actual val PermissionLocalNetwork: PermissionInfo = passivePermission("local_network", "Local network")
actual val RuntimeLocalNetworkPermissionRequired: Boolean = false

private fun passivePermission(id: String, label: String): PermissionInfo = PermissionInfo(
    permission = id,
    displayName = { Text(label) },
    usage = { Text("Managed by iOS") },
    required = true,
)

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
private enum class MediaPermission {
    Camera,
    Microphone;

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

private fun PermissionInfo.mediaPermission(): MediaPermission? = when (permission) {
    CAMERA_PERMISSION -> MediaPermission.Camera
    RECORD_AUDIO_PERMISSION -> MediaPermission.Microphone
    else -> null
}

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
        scope.launch {
            pending.forEach { (_, media) -> media.requestAccess() }
            refresh()
        }
    }

    override fun proceedFromRationale() {
        dismissRationale()
    }

    override fun cancelPermissionRequest() {
        dismissRationale()
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }

    private fun dismissRationale() {
        showRationaleDialog = false
        currentRationalePermissions = emptyList()
    }

    private fun readStatuses(): Map<String, PermissionStatus> =
        managed.associate { (info, media) -> info.permission to media.status() }
}
