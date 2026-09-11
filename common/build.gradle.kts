import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    android {
        namespace = "me.rerere.common"
        compileSdk = 37
        minSdk = 26

        withHostTest {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    iosArm64()
    iosSimulatorArm64()

    swiftPMDependencies {
        iosMinimumDeploymentTarget.set("15.0")
        localSwiftPackage(
            directory = layout.projectDirectory.dir("src/nativeInterop/ZipArchiveBridge"),
            products = listOf(product("ZipArchiveBridge")),
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.io.core)
            api(libs.ktor.client.core)
            implementation(libs.dokar.quickjs)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // The Android and desktop ZIP adapters use the same Java API as 2.4.5.
        val javaZipTest by creating {
            dependsOn(commonTest.get())
        }
        named("jvmTest") { dependsOn(javaZipTest) }
        named("androidHostTest") { dependsOn(javaZipTest) }
        // Android host tests cannot load the Android JNI runtime; use device tests there.
        val javaScriptTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
        named("jvmTest") { dependsOn(javaScriptTest) }
        named("iosTest") { dependsOn(javaScriptTest) }
        androidMain.dependencies {
            api(libs.okhttp)
            api(libs.okhttp.sse)
            api(libs.okhttp.logging)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
        named("androidDeviceTest") {
            dependsOn(javaScriptTest)
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}
