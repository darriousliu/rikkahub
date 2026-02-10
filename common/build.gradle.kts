plugins {
    id("multiplatform")
}

kotlin {
    androidLibrary {
        namespace = "me.rerere.common"
    }
    sourceSets {
        commonMain.dependencies {
            // Coil
            api(libs.coil.compose)
            // ktor
            api(libs.bundles.ktor)

            // kotlinx
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)

            // KMP Utils
            api(libs.mp.stools)
            api(libs.filekit.core)
            api(libs.filekit.dialogs.compose)

            // atomics
            api(libs.atomicfu)

            // Firebase
            api(libs.gitlive.firebase.analytics)
            api(libs.gitlive.firebase.crashlytics)
            api(libs.gitlive.firebase.config)

            api(libs.kermit)

            api(libs.okio)

            // koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            api(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)

            api(libs.ktor.client.okhttp)

            // pebble (template engine)
            api(libs.pebble)

            // floating
            // https://github.com/Petterpx/FloatingX
            api("io.github.petterpx:floatingx:2.3.7")
            api("io.github.petterpx:floatingx-compose:2.3.7")
        }
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
        }
    }
}
