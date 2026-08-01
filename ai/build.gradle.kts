import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "me.rerere.ai"
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
        }
    }

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":common"))
            implementation(libs.androidx.core.ktx)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.material3)
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.okhttp.mockwebserver)
                implementation(libs.ktor.client.okhttp)
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
