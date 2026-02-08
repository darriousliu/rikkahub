package me.rerere.rikkahub.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import me.rerere.common.PlatformContext
import me.rerere.common.utils.PlatformPebbleEngine
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.rikkahub.utils.DocumentReader
import me.rerere.rikkahub.utils.HtmlEscaper
import me.rerere.rikkahub.utils.ProviderQRCodeScanner
import me.rerere.rikkahub.utils.QRCodeDecoder
import me.rerere.rikkahub.utils.QRCodeEncoder
import me.rerere.rikkahub.utils.QRCodeScanner
import me.rerere.rikkahub.utils.ZipUtil
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { PlatformContext.INSTANCE } bind PlatformContext::class

    single {
        Room.databaseBuilder<AppDatabase>(FileKit.databasesDir.resolve("rikka_hub").path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(Migration_6_7, Migration_11_12)
            .build()
    }

    single {
        PlatformPebbleEngine()
    }

    single { TemplateTransformer(settingsStore = get()) }
}

fun KoinApplication.initIOSKoin(
    di: List<Any>,
) {
    val htmlEscaper = di.find { it is HtmlEscaper } as? HtmlEscaper
    val qrCodeProvider = di.find { it is ProviderQRCodeScanner } as? ProviderQRCodeScanner
    val qrCodeDecoder = di.find { it is QRCodeDecoder } as? QRCodeDecoder
    val documentReader = di.find { it is DocumentReader } as? DocumentReader
    val qrCodeEncoder = di.find { it is QRCodeEncoder } as? QRCodeEncoder
    val zipUtil = di.find { it is ZipUtil } as? ZipUtil
    modules(
        module {
            htmlEscaper?.let { single<HtmlEscaper> { htmlEscaper } }
            qrCodeProvider?.let {
                factory<QRCodeScanner> {
                    qrCodeProvider.factory(it[0])
                }
            }
            qrCodeDecoder?.let { single<QRCodeDecoder> { qrCodeDecoder } }
            documentReader?.let { single<DocumentReader> { documentReader } }
            qrCodeEncoder?.let { single<QRCodeEncoder> { qrCodeEncoder } }
            zipUtil?.let { single<ZipUtil> { zipUtil } }
        }
    )
}
