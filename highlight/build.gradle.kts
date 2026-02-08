plugins {
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("multiplatform")
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.highlight"

        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(libs.compose.resources)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.quickjs.kt)
            implementation(libs.quickjs.kt.converter.serialization)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.ui)
            implementation(libs.androidx.ui.graphics)
            implementation(libs.androidx.ui.tooling.preview)
            implementation(libs.androidx.material3)
        }
    }
}

