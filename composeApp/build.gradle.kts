@file:OptIn(ExperimentalSwiftExportDsl::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.composeapp"

        androidResources {
            enable = true
        }
    }

    listOf(
//        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(project(":common"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // modules
            implementation(project(":ai"))
            implementation(project(":highlight"))
            implementation(project(":search"))
            implementation(project(":tts"))
            api(project(":common"))
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.preview)
            implementation("org.jetbrains.compose.material3.adaptive:adaptive:1.2.0")

            // lifecycle
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            // Navigation
            implementation(libs.navigation.compose)

            // DataStore
            implementation(libs.androidx.datastore.preferences)

            // koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)


            // jetbrains markdown parser
            implementation(libs.jetbrains.markdown)

            // coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)

            // serialization
            implementation(libs.kotlinx.serialization.json)

            // WebDav
            implementation(libs.dav4kmp)

            // Room
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation(libs.androidx.sqlite.bundled)

            // Paging3
//            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.paging.compose.get().toString()) {
                exclude(group = "androidx.compose.ui", module = "ui-android")
            }

            // Reorderable (https://github.com/Calvin-LL/Reorderable/)
            implementation(libs.reorderable)

            // lucide icons
            implementation(libs.lucide.icons)

            // image viewer
            implementation(libs.image.viewer)

            // Toast (Sonner)
            implementation(libs.sonner)

            // mcp
            implementation(libs.mcp.client)

            implementation(kotlin("reflect"))

            // Ktor
            implementation(libs.bundles.ktor)
            implementation(libs.ktorfit.lib.lite)

            // ksoup
            implementation(libs.ksoup)

            // quickjs
            implementation(libs.quickjs.kt)
            implementation(libs.quickjs.kt.converter.serialization)

            // cache
            implementation(libs.cache4k)
            // Latex
            implementation(libs.katex.core)
            // Permission
            implementation(libs.calf.permissions)


        }
        androidMain.dependencies {
            implementation(project(":document"))
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.profileinstaller)

            // koin
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)

            // Compose
            implementation(libs.androidx.activity.compose)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui)
            implementation(libs.androidx.ui.android)
            implementation(libs.androidx.ui.graphics)
            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.androidx.material3)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)

            // Navigation 2
            implementation(libs.androidx.navigation2)

            // Navigation 3
//    implementation(libs.androidx.navigation3.runtime)
//    implementation(libs.androidx.navigation3.ui)
//    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
//    implementation(libs.androidx.material3.adaptive.navigation3)

            // Firebase
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.config)

            // Image metadata extractor
            // https://github.com/drewnoakes/metadata-extractor
            implementation(libs.metadata.extractor)

            // ucrop
            implementation(libs.ucrop)

            // pebble (template engine)
            implementation(libs.pebble)

            // zxing
            implementation(libs.zxing.core)

            // quickie (qrcode scanner)
            implementation(libs.quickie.bundled)
            implementation(libs.barcode.scanning)
            implementation(libs.androidx.camera.core)

            // Apache Commons Text
            implementation(libs.commons.text)

            // JLatexMath
            // https://github.com/rikkahub/jlatexmath-android
            implementation(libs.jlatexmath)
            implementation(libs.jlatexmath.font.greek)
            implementation(libs.jlatexmath.font.cyrillic)

            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

buildkonfig {
    packageName = "me.rerere.rikkahub.buildkonfig"
    objectName = "BuildConfig"
    defaultConfigs {
        buildConfigField(INT, "VERSION_CODE", properties["versionCode"].toString())
        buildConfigField(STRING, "VERSION_NAME", properties["versionName"].toString())
        buildConfigField(BOOLEAN, "DEBUG", properties["debug"].toString())
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
    kspAndroid(libs.androidx.room.compiler)
    kspIosArm64(libs.androidx.room.compiler)
    kspIosSimulatorArm64(libs.androidx.room.compiler)
//    kspIosX64(libs.androidx.room.compiler)

    // Leak Canary
//    debugImplementation(libs.leakcanary.android)
}


ktorfit {
    compilerPluginVersion.set("2.3.3")
}
