import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
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
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
            optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
            optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
            optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
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
            implementation(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(project(":common"))
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.common)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.material3)
        }
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.json.jvm)
                implementation(libs.okhttp.mockwebserver)
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
