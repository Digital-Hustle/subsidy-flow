// Общее для всех Java-модулей: toolchain, компиляция, checkstyle, тесты, покрытие.
plugins {
    java
    checkstyle
    jacoco
}

val catalog = the<VersionCatalogsExtension>().named("libs")

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked"))
}

checkstyle {
    toolVersion = catalog.findVersion("checkstyle").get().requiredVersion
    val dir = rootProject.file("config/checkstyle")
    configDirectory = dir
    configFile = dir.resolve("checkstyle.xml")
    configProperties = mapOf(
        "checkstyle.dir" to dir.absolutePath,
        "checkstyle.cache.file" to layout.buildDirectory.file("checkstyle/cache.properties").get().asFile.absolutePath,
    )
    maxWarnings = 0
}
tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.path.contains("generated") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    finalizedBy(tasks.named("jacocoTestReport"))
}
tasks.named<JacocoReport>("jacocoTestReport") {
    reports { xml.required = true; html.required = false }
}
