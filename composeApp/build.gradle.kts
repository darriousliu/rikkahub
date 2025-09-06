import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    alias(libs.plugins.chaquo.python)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ktorfit)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
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

            // Room
            implementation(libs.androidx.room.paging)

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

            // Permission
            implementation(libs.permissions.compose)

            // JLatexMath
            // https://github.com/rikkahub/jlatexmath-android
            implementation(libs.jlatexmath)
            implementation(libs.jlatexmath.font.greek)
            implementation(libs.jlatexmath.font.cyrillic)

            // modules
            implementation(project(":ai"))
            implementation(project(":highlight"))
            implementation(project(":search"))
            implementation(project(":rag"))
            implementation(project(":tts"))
            implementation(project(":common"))

            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

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
            implementation(libs.modelcontextprotocol.kotlin.sdk)

            implementation(kotlin("reflect"))

            // Ktor
            implementation(libs.bundles.ktor)
            implementation(libs.ktorfit.lib.lite)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.rerere.rikkahub"
        minSdk = 26
        targetSdk = 36
        versionCode = 98
        versionName = "1.5.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        defaultConfig {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
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
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

chaquopy {
    defaultConfig {
        version = "3.13"
        if (Os.isFamily(Os.FAMILY_MAC)) buildPython("/opt/homebrew/bin/python3")
        pip {
            install("pypdf")
            install("python-docx")
        }
    }
}

dependencies {
    ksp(libs.androidx.room.compiler)

    // Leak Canary
    debugImplementation(libs.leakcanary.android)

    // tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
