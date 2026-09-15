// Build the same pinned SQLite extension for desktop and both Apple targets.
// Android uses this CMake project through externalNativeBuild.
val cmake = providers.environmentVariable("CMAKE_COMMAND").orElse("cmake")
val source = rootProject.file("native/simple")

fun simpleBuild(target: String, vararg options: String): TaskProvider<Exec> {
    val buildDir = layout.buildDirectory.dir("simple/$target")
    val installDir = layout.buildDirectory.dir("simple/$target/install")
    val configure = tasks.register<Exec>("configureSimple$target") {
        inputs.file(source.resolve("CMakeLists.txt"))
        inputs.property("options", options.toList())
        outputs.file(buildDir.map { it.file("CMakeCache.txt") })
        commandLine(
            listOf(cmake.get(), "-S", source.absolutePath, "-B", buildDir.get().asFile.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_INSTALL_PREFIX=${installDir.get().asFile.absolutePath}") + options
        )
    }
    return tasks.register<Exec>("buildSimple$target") {
        dependsOn(configure)
        inputs.file(source.resolve("CMakeLists.txt"))
        inputs.property("options", options.toList())
        outputs.dir(installDir)
        commandLine(cmake.get(), "--build", buildDir.get().asFile.absolutePath,
            "--config", "Release", "--target", "install", "--parallel", "4")
    }
}

val jvm = simpleBuild("Jvm")
tasks.register<Sync>("prepareSimpleJvmResources") {
    dependsOn(jvm)
    from(layout.buildDirectory.dir("simple/Jvm/install")) { into("native/simple") }
    into(layout.buildDirectory.dir("generated/simpleJvmResources"))
}

listOf("IosArm64" to "iphoneos", "IosSimulatorArm64" to "iphonesimulator").forEach { (target, sdk) ->
    val build = simpleBuild(target,
        "-DCMAKE_SYSTEM_NAME=iOS", "-DCMAKE_OSX_SYSROOT=$sdk",
        "-DCMAKE_OSX_ARCHITECTURES=arm64", "-DCMAKE_OSX_DEPLOYMENT_TARGET=15.0")
    tasks.matching { it.name.endsWith("Framework$target") && it.name.startsWith("link") }.configureEach {
        dependsOn(build)
    }
}

val iosTestResources = tasks.register<Copy>("prepareSimpleIosTestResources") {
    dependsOn("buildSimpleIosSimulatorArm64")
    from(layout.buildDirectory.dir("simple/IosSimulatorArm64/install/simple.framework"))
    into(layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest/Frameworks/simple.framework"))
}
tasks.matching { it.name == "iosSimulatorArm64Test" }.configureEach {
    dependsOn(iosTestResources)
}
