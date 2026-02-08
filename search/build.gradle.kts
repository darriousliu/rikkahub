plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.search"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.resources)
            implementation(libs.compose.material3)

            implementation(project(":ai"))
            implementation(libs.bundles.ktor)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.ksoup)
            implementation(libs.ksoup.network)
        }
        androidMain.dependencies {
            implementation(project(":ai"))
            implementation(libs.bundles.ktor)
            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.material3)
        }
    }
}
