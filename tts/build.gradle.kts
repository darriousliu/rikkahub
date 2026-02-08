plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.tts"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3)

            implementation(project(":common"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.common)

            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.material3)
        }
    }
}
