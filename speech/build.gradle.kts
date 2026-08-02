import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val javafxPlatformClassifier = when {
    System.getProperty("os.name").startsWith("Mac") -> {
        if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "mac-aarch64" else "mac"
    }
    System.getProperty("os.name").startsWith("Windows") -> "win"
    System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "linux-aarch64"
    else -> "linux"
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "me.rerere.speech"
        compileSdk = 37
        minSdk = 26

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
            optIn.add("kotlin.time.ExperimentalTime")
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.common)
        }
        jvmMain.dependencies {
            implementation("org.openjfx:javafx-base:${libs.versions.javafx.get()}:$javafxPlatformClassifier")
            implementation("org.openjfx:javafx-graphics:${libs.versions.javafx.get()}:$javafxPlatformClassifier")
            implementation("org.openjfx:javafx-media:${libs.versions.javafx.get()}:$javafxPlatformClassifier")
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}
