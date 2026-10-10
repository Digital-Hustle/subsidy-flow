#!/usr/bin/env bash
# Создаёт скелет Gradle-монорепы бэкенда (один раз, на старте хакатона).
# Использование: init-backend.sh <dir> [group-root=ru.<case>]
# <case> = имя папки кейса над <dir> (proxodnaya | documents | subsidy) или env PROJECT; по умолчанию proxodnaya.
set -euo pipefail
DIR=${1:?usage: init-backend.sh <dir>}
ABS_DIR=$(mkdir -p "$DIR" && cd "$DIR" && pwd)
PROJECT=${PROJECT:-$(basename "$(dirname "$ABS_DIR")")}
case "$PROJECT" in proxodnaya|documents|subsidy) ;; *) PROJECT=proxodnaya ;; esac
ROOT_PKG=${2:-ru.$PROJECT}
SKILL=$(cd "$(dirname "$0")/.." && pwd)
GRADLE_VERSION=9.3.0
mkdir -p "$DIR" && cd "$DIR"
[ -f settings.gradle.kts ] && { echo "settings.gradle.kts уже есть — выхожу"; exit 1; }

cat > settings.gradle.kts <<'EOF'
rootProject.name = "proxodnaya-services"

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
EOF

mkdir -p gradle
cat > gradle/libs.versions.toml <<'EOF'
[versions]
spring-boot = "3.5.16"
spring-cloud = "2025.0.3"
spring-dependency-management = "1.1.7"
jooq = "3.20.9"
jooq-gradle = "10.2.1"
liquibase-gradle = "3.1.0"
liquibase = "4.31.1"          # = версия из BOM Boot 3.5.16; нужна build-logic для плагина
postgresql = "42.7.11"
picocli = "4.7.5"
springdoc = "2.8.17"
mapstruct = "1.6.3"
lombok-mapstruct-binding = "0.2.0"
resilience4j = "2.4.0"
archunit = "1.5.0"
uuid-creator = "6.1.1"
checkstyle = "13.0.0"

[libraries]
spring-boot-gradle-plugin = { module = "org.springframework.boot:spring-boot-gradle-plugin", version.ref = "spring-boot" }
dependency-management-plugin = { module = "io.spring.gradle:dependency-management-plugin", version.ref = "spring-dependency-management" }
jooq-gradle-plugin = { module = "nu.studer:gradle-jooq-plugin", version.ref = "jooq-gradle" }
liquibase-gradle-plugin = { module = "org.liquibase:liquibase-gradle-plugin", version.ref = "liquibase-gradle" }
liquibase-core = { module = "org.liquibase:liquibase-core", version.ref = "liquibase" }

springdoc-webmvc = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
springdoc-webflux = { module = "org.springdoc:springdoc-openapi-starter-webflux-ui", version.ref = "springdoc" }
mapstruct = { module = "org.mapstruct:mapstruct", version.ref = "mapstruct" }
mapstruct-processor = { module = "org.mapstruct:mapstruct-processor", version.ref = "mapstruct" }
lombok-mapstruct-binding = { module = "org.projectlombok:lombok-mapstruct-binding", version.ref = "lombok-mapstruct-binding" }
resilience4j-spring-boot = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }
uuid-creator = { module = "com.github.f4b6a3:uuid-creator", version.ref = "uuid-creator" }
archunit = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
picocli = { module = "info.picocli:picocli", version.ref = "picocli" }
EOF

mkdir -p build-logic/src/main/kotlin
cat > build-logic/settings.gradle.kts <<'EOF'
rootProject.name = "build-logic"
dependencyResolutionManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
EOF
cat > build-logic/build.gradle.kts <<'EOF'
plugins { `kotlin-dsl` }
dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.dependency.management.plugin)
    implementation(libs.jooq.gradle.plugin)
    implementation(libs.liquibase.gradle.plugin)
    implementation(libs.liquibase.core)
}
EOF

cat > build-logic/src/main/kotlin/proxodnaya.java.gradle.kts <<'EOF'
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
EOF

cat > build-logic/src/main/kotlin/proxodnaya.library.gradle.kts <<'EOF'
// Библиотека (libs/*): без bootJar, версии из BOM Spring Boot / Cloud.
plugins {
    id("proxodnaya.java")
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
EOF

cat > build-logic/src/main/kotlin/proxodnaya.service.gradle.kts <<'EOF'
// Spring Boot сервис на servlet-стеке (все, кроме gateway).
plugins {
    id("proxodnaya.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${catalog.findVersion("spring-cloud").get()}")
    }
}

dependencies {
    implementation(project(":libs:common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(lib("springdoc-webmvc"))
    implementation(lib("resilience4j-spring-boot"))
    implementation(lib("uuid-creator"))
    implementation(lib("mapstruct"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor(lib("mapstruct-processor"))
    annotationProcessor(lib("lombok-mapstruct-binding"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(lib("archunit"))
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}
tasks.named<Jar>("jar") { enabled = false }

// OpenAPI-контракт: ./gradlew :svc:exportOpenApi → ../contracts/openapi/<svc>.json
tasks.register<Test>("exportOpenApi") {
    group = "verification"
    description = "Выгружает OpenAPI сервиса в contracts/openapi"
    val test = the<SourceSetContainer>()["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*OpenApiContractIntegrationTest"); isFailOnNoMatchingTests = false }
    systemProperty("contract.dir", rootProject.file("../contracts/openapi").absolutePath)
    outputs.upToDateWhen { false }
}
EOF

cat > build-logic/src/main/kotlin/proxodnaya.gateway.gradle.kts <<'EOF'
// Spring Cloud Gateway (reactive): без libs:common и servlet-стартеров.
plugins {
    id("proxodnaya.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${catalog.findVersion("spring-cloud").get()}")
    }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(lib("archunit"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName = "${project.name}.jar" }
tasks.named<Jar>("jar") { enabled = false }
EOF

cat > build-logic/src/main/kotlin/proxodnaya.jooq.gradle.kts <<'EOF'
// Сервис с БД: Liquibase (update из Gradle) + jOOQ codegen из живой схемы.
// Подключение: DB_HOST/DB_PORT/DB_ADMIN_USER/DB_ADMIN_PASSWORD, имя БД = имя сервиса.
import org.jooq.meta.jaxb.Logging

plugins {
    id("proxodnaya.service")
    id("org.liquibase.gradle")
    id("nu.studer.jooq")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()
val jooqVersion = catalog.findVersion("jooq").get().requiredVersion

fun env(name: String, default: String) = providers.environmentVariable(name).getOrElse(default)
val dbUrl = "jdbc:postgresql://${env("DB_HOST", "localhost")}:${env("DB_PORT", "5432")}/${project.name}"
val dbUser = env("DB_ADMIN_USER", "postgres")
val dbPassword = env("DB_ADMIN_PASSWORD", "postgres")   // только локальная БД разработчика/CI

extra["jooq.version"] = jooqVersion   // Boot 3.5 управляет 3.19; берём 3.20 как в certis

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
    jooqGenerator(lib("postgresql"))
    liquibaseRuntime("org.liquibase:liquibase-core")
    liquibaseRuntime(lib("picocli"))
    liquibaseRuntime(lib("postgresql"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

liquibase {
    activities.register("main") {
        arguments = mapOf(
            // searchPath = resources: в databasechangelog пишется тот же путь, что и при старте приложения из classpath
            "searchPath" to layout.projectDirectory.dir("src/main/resources").asFile.absolutePath,
            "changelogFile" to "db/changelog/db.changelog-master.yaml",
            "url" to dbUrl, "username" to dbUser, "password" to dbPassword,
            "contexts" to "dev",
        )
    }
    runList = "main"
}

jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = Logging.WARN
                jdbc = org.jooq.meta.jaxb.Jdbc().apply { driver = "org.postgresql.Driver"; url = dbUrl; user = dbUser; password = dbPassword }
                generator = org.jooq.meta.jaxb.Generator().apply {
                    database = org.jooq.meta.jaxb.Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "databasechangelog|databasechangeloglock"
                    }
                    generate = org.jooq.meta.jaxb.Generate().apply {
                        isDeprecated = false; isRecords = true; isJavaTimeTypes = true; isFluentSetters = false
                    }
                    target = org.jooq.meta.jaxb.Target().apply {
                        packageName = "ru.proxodnaya.${project.name}.jooq"
                        directory = "build/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}
// compileJava зависит от generateJooq: первой сборке нужна БД (локально — compose, в CI — service container).
// allInputsDeclared = true → задача up-to-date, пока не поменялись миграции/конфиг.
tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
    allInputsDeclared = true
    inputs.dir("src/main/resources/db/changelog")
    mustRunAfter("update")
}
EOF

mkdir -p config/checkstyle
cp "$SKILL"/assets/checkstyle/*.xml config/checkstyle/

# libs/common — каркас; содержимое (ProblemDetail, CurrentUser, Outbox…) пишется на хакатоне по references/.
PKG_DIR=libs/common/src/main/java/$(echo "$ROOT_PKG" | tr . /)/common
mkdir -p "$PKG_DIR/mapper"
cat > libs/common/build.gradle.kts <<'EOF'
plugins { id("proxodnaya.library") }
dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework.kafka:spring-kafka")
    api(libs.mapstruct)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
EOF
cat > "$PKG_DIR/mapper/BaseMapperConfig.java" <<EOF
package $ROOT_PKG.common.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants.ComponentModel;
import org.mapstruct.ReportingPolicy;

/**
 * Общая конфигурация MapStruct: Spring-бины, незамапленное поле — ошибка компиляции.
 */
@MapperConfig(
        componentModel = ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BaseMapperConfig {
}
EOF

cat > Dockerfile <<'EOF'
# Один Dockerfile на все Java-сервисы: docker build --build-arg SERVICE=people -f Dockerfile .
# jar собирает Gradle заранее (jOOQ генерируется из БД, внутри docker build её нет).
FROM eclipse-temurin:21-jre AS layers
ARG SERVICE
WORKDIR /x
COPY ${SERVICE}/build/libs/${SERVICE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /out

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 app
WORKDIR /app
COPY --from=layers /out/dependencies/ ./
COPY --from=layers /out/spring-boot-loader/ ./
COPY --from=layers /out/snapshot-dependencies/ ./
COPY --from=layers /out/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
EOF
cat > .dockerignore <<'EOF'
**
!*/build/libs/*.jar
EOF

cat > .gitignore <<'EOF'
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
*.iml
.idea/
EOF

# Имя кейса в сгенерированных файлах (шаблоны написаны для proxodnaya)
if [ "$PROJECT" != proxodnaya ]; then
  for f in build-logic/src/main/kotlin/proxodnaya.*.gradle.kts; do mv "$f" "${f/proxodnaya./$PROJECT.}"; done
  grep -rlI 'proxodnaya' settings.gradle.kts build-logic libs config 2>/dev/null | xargs -r sed -i "s/proxodnaya/$PROJECT/g"
fi

# Gradle wrapper
# (генерируем в пустой папке, чтобы не конфигурировать ещё не готовую сборку)
GRADLE_BIN=$(command -v gradle || true)
if [ -z "$GRADLE_BIN" ]; then
  CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/gradle-dist; mkdir -p "$CACHE"
  [ -d "$CACHE/gradle-$GRADLE_VERSION" ] || { curl -fsSL -o "$CACHE/g.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" && unzip -q "$CACHE/g.zip" -d "$CACHE" && rm "$CACHE/g.zip"; }
  GRADLE_BIN="$CACHE/gradle-$GRADLE_VERSION/bin/gradle"
fi
TMPW=$(mktemp -d); (cd "$TMPW" && touch settings.gradle.kts && "$GRADLE_BIN" -q wrapper --gradle-version "$GRADLE_VERSION")
cp -r "$TMPW"/gradlew "$TMPW"/gradlew.bat "$TMPW"/gradle . && rm -rf "$TMPW"
echo "Готово: $DIR. Дальше: $(dirname "$0")/new-service.sh <svc> <port> <Owner> [--gateway|--no-db]"
