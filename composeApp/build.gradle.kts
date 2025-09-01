@file:OptIn(ExperimentalSwiftExportDsl::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
        androidMain.dependencies {
            implementation(project(":document"))
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.browser)

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

            // WebDav
            implementation(libs.dav4jvm.get().toString()) {
                exclude(group = "org.ogce", module = "xpp3")
            }

            // Apache Commons Text
            implementation(libs.commons.text)

            // JLatexMath
            // https://github.com/rikkahub/jlatexmath-android
            implementation(libs.jlatexmath)
            implementation(libs.jlatexmath.font.greek)
            implementation(libs.jlatexmath.font.cyrillic)

            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            // modules
            implementation(project(":ai"))
            implementation(project(":highlight"))
            implementation(project(":search"))
            implementation(project(":tts"))
            api(project(":common"))
            // Compose
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.ui.backhandler)
            implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha04")
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
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
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

buildkonfig {
    packageName = "me.rerere.rikkahub.buildkonfig"
    objectName = "BuildConfig"
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.rerere.rikkahub"
        minSdk = 26
        targetSdk = 36
        versionCode = 110
        versionName = "1.6.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    splits {
        abi {
            // AppBundle tasks usually contain "bundle" in their name
            //noinspection WrongGradleMethod
            val isBuildingBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
            isEnable = !isBuildingBundle
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")

            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))

                val storeFilePath = localProperties.getProperty("storeFile")
                val storePasswordValue = localProperties.getProperty("storePassword")
                val keyAliasValue = localProperties.getProperty("keyAlias")
                val keyPasswordValue = localProperties.getProperty("keyPassword")

                if (storeFilePath != null && storePasswordValue != null &&
                    keyAliasValue != null && keyPasswordValue != null
                ) {
                    storeFile = file(storeFilePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
            buildkonfig.defaultConfigs {
                buildConfigField(STRING, "VERSION_NAME", android.defaultConfig.versionName)
                buildConfigField(STRING, "VERSION_CODE", android.defaultConfig.versionCode.toString())
                buildConfigField(STRING, "APPLICATION_ID", android.defaultConfig.applicationId)
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
            buildkonfig.defaultConfigs {
                buildConfigField(STRING, "VERSION_NAME", android.defaultConfig.versionName)
                buildConfigField(STRING, "VERSION_CODE", android.defaultConfig.versionCode.toString())
                buildConfigField(STRING, "APPLICATION_ID", "${android.defaultConfig.applicationId}.debug")
            }
        }
        create("baseline") {
            initWith(getByName("release"))
            matchingFallbacks.add("release")
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            isProfileable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    applicationVariants.all {
        outputs.all {
            this as com.android.build.gradle.internal.api.ApkVariantOutputImpl

            val variantName = name
            val apkName = "rikkahub_" + defaultConfig.versionName + "_" + variantName + ".apk"

            outputFileName = apkName
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

tasks.register("buildAll") {
    dependsOn("assembleRelease", "bundleRelease")
    description = "Build both APK and AAB"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":app:baselineprofile"))

    kspAndroid(libs.androidx.room.compiler)
    kspIosArm64(libs.androidx.room.compiler)
    kspIosSimulatorArm64(libs.androidx.room.compiler)
//    kspIosX64(libs.androidx.room.compiler)

    // Leak Canary
//    debugImplementation(libs.leakcanary.android)

    // tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
