// Сервис с БД: Liquibase (update из Gradle) + jOOQ codegen из живой схемы.
// Подключение: DB_HOST/DB_PORT/DB_ADMIN_USER/DB_ADMIN_PASSWORD, имя БД = имя сервиса.
import org.jooq.meta.jaxb.Logging

plugins {
    id("subsidy.service")
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
                        packageName = "ru.subsidy.${if (project.name == "package") "package_" else project.name}.jooq"
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
