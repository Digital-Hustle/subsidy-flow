rootProject.name = "subsidy-services"

pluginManagement {
    includeBuild("build-logic")
    repositories { gradlePluginPortal() }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

// Каждая папка с build.gradle.kts — модуль (как в sueta-rnd-back). libs/* — библиотеки.
fun includeDir(parent: File, prefix: String) {
    parent.listFiles()?.filter { it.isDirectory && it.name != "build-logic" && File(it, "build.gradle.kts").exists() }?.sorted()?.forEach {
        include("$prefix${it.name}")
        project(":$prefix${it.name}").projectDir = it
    }
}
includeDir(rootDir, "")
includeDir(File(rootDir, "libs"), "libs:")
