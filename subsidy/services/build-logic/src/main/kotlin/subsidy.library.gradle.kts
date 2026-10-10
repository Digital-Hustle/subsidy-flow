// Библиотека (libs/*): без bootJar, версии из BOM Spring Boot / Cloud.
plugins {
    id("subsidy.java")
    `java-library`
    id("io.spring.dependency-management")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${catalog.findVersion("spring-boot").get()}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${catalog.findVersion("spring-cloud").get()}")
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
