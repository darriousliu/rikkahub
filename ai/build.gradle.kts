plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.ai"

        withHostTest {}

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.compose.runtime)

            // Ktor
            implementation(libs.bundles.ktor)
            implementation(libs.ktor.client.auth)

            // kotlinx
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)

            // Log
            api(libs.kermit)

            // okio
            api(libs.okio)

            // Crypto
            api(project.dependencies.platform(libs.cryptography.bom))
            api(libs.cryptography.core)
        }
        androidMain.dependencies {
            // Compose
            implementation(libs.androidx.core.ktx)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.material3)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
//        androidHostTest.dependencies {
//            implementation(libs.junit)
//            implementation(libs.androidx.junit)
//            implementation(libs.androidx.espresso.core)
//        }
//        androidDeviceTest.dependencies {
//            implementation(libs.junit)
//            implementation(libs.androidx.junit)
//            implementation(libs.androidx.espresso.core)
//        }
    }
}
