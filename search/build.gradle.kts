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
        namespace = "me.rerere.search"
        compileSdk = 37
        minSdk = 23

        androidResources.enable = true
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
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":ai"))
            implementation(project(":common"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.http)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.fleeksoft.ksoup)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "me.rerere.search.generated.resources"
}
