package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ncgroup.kscan.BarcodeResult

public sealed interface QrScanResult {
    public data class Success(val content: String) : QrScanResult

    public data class Failure(val cause: Throwable) : QrScanResult

    public data object Canceled : QrScanResult
}

public interface QrScanner {
    @Composable
    public fun Content(
        modifier: Modifier,
        onResult: (QrScanResult) -> Unit,
    )
}

@Composable
public expect fun rememberPlatformQrScanner(): QrScanner?

public fun interface QrImageDecoder {
    public suspend fun decode(imageBytes: ByteArray): QrScanResult
}

public class QrCodeMatrix internal constructor(
    public val width: Int,
    public val height: Int,
    private val darkModules: BooleanArray,
) {
    init {
        require(width > 0 && height > 0) { "QR code dimensions must be positive" }
        require(darkModules.size == width * height) { "QR code matrix size does not match its dimensions" }
    }

    public operator fun get(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until height) { "QR code coordinate is out of bounds" }
        return darkModules[y * width + x]
    }
}

public fun interface QrCodeRenderer {
    public fun render(content: String, size: Int): Result<QrCodeMatrix>
}

public class PlatformQrCodeRenderer : QrCodeRenderer {
    override fun render(content: String, size: Int): Result<QrCodeMatrix> =
        platformRenderQrCode(content = content, size = size)
}

public class KScanQrImageDecoder : QrImageDecoder {
    override suspend fun decode(imageBytes: ByteArray): QrScanResult = platformDecodeQrImage(imageBytes)
}

internal expect fun platformRenderQrCode(content: String, size: Int): Result<QrCodeMatrix>

internal expect suspend fun platformDecodeQrImage(imageBytes: ByteArray): QrScanResult

internal suspend fun decodeQrImageWithKScan(imageBytes: ByteArray): QrScanResult =
    suspendCancellableCoroutine { continuation ->
        org.ncgroup.kscan.scanImage(
            imageBytes = imageBytes,
            codeTypes = listOf(org.ncgroup.kscan.BarcodeFormat.FORMAT_QR_CODE),
        ) { result ->
            if (continuation.isActive) {
                continuation.resume(result.toQrScanResult())
            }
        }
    }

internal fun BarcodeResult.toQrScanResult(): QrScanResult = when (this) {
    is BarcodeResult.OnSuccess -> QrScanResult.Success(barcode.data)
    is BarcodeResult.OnFailed -> QrScanResult.Failure(exception)
    BarcodeResult.OnCanceled -> QrScanResult.Canceled
}
