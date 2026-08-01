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

public fun interface QrImageDecoder {
    public suspend fun decode(imageBytes: ByteArray): QrScanResult
}

public class KScanQrImageDecoder : QrImageDecoder {
    override suspend fun decode(imageBytes: ByteArray): QrScanResult = platformDecodeQrImage(imageBytes)
}

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
