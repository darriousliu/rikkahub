import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-multiplatform`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlinx.cinterop.BetaInteropApi",
            "kotlin.uuid.ExperimentalUuidApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

//    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

