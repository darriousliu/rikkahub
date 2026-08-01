package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.ncgroup.kscan.BarcodeFormat
import org.ncgroup.kscan.ScannerUiOptions
import org.ncgroup.kscan.ScannerView

public class KScanQrScanner(
    private val title: String = "Scan QR code",
) : QrScanner {
    @Composable
    override fun Content(
        modifier: Modifier,
        onResult: (QrScanResult) -> Unit,
    ) {
        ScannerView(
            modifier = modifier,
            codeTypes = listOf(BarcodeFormat.FORMAT_QR_CODE),
            scannerUiOptions = ScannerUiOptions(headerTitle = title),
            result = { onResult(it.toQrScanResult()) },
        )
    }
}

@Composable
public actual fun rememberPlatformQrScanner(): QrScanner? = remember { KScanQrScanner() }
