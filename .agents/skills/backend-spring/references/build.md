# Сборка, Docker, CI

## Раскладка (`subsidy/services/`)
```
settings.gradle.kts          # includeBuild("build-logic") + авто-include каждой папки с build.gradle.kts (как в sueta)
gradle/libs.versions.toml    # ВСЕ версии здесь; в build.gradle.kts сервисов версий нет
gradle/wrapper/  gradlew     # один wrapper на всё (в sueta — свой у каждого сервиса)
build-logic/src/main/kotlin/
  proxodnaya.java.gradle.kts     # toolchain 21, -parameters, -Werror для unchecked, checkstyle, jacoco, JUnit 5
  proxodnaya.service.gradle.kts  # + Spring Boot, BOM Boot/Cloud, общий набор стартеров, libs:common, bootJar
  proxodnaya.jooq.gradle.kts     # + Liquibase-плагин и jOOQ codegen из БД сервиса
config/checkstyle/checkstyle.xml # из sueta (IntelliJ-стиль, 140 символов), suppressions рядом
libs/common/                     # ProblemDetail, ErrorCode, CurrentUser, SecurityConfig, ServiceClients,
                                 # Outbox, OutboxRelay, IdempotentConsumer, EventEnvelope, Topics, BaseMapperConfig
gateway/ people/ access/ shift/ assistant/   # Java-сервисы
face/                            # Python, вне Gradle (pyproject.toml)
Dockerfile                       # один на все Java-сервисы: --build-arg SERVICE=people
```
Создать скелет (один раз, на старте хакатона): `.agents/skills/backend-spring/scripts/init-backend.sh subsidy/services`. Новый сервис: `scripts/new-service.sh <svc> <port> <Owner>`.

## build.gradle.kts сервиса
```kotlin
plugins {
    id("proxodnaya.service")
    id("proxodnaya.jooq")          // если у сервиса есть БД
}
dependencies {
    implementation(libs.spring.boot.starter.web)       // gateway: spring-cloud-starter-gateway-server-webflux
    implementation(libs.spring.kafka)
    implementation(libs.resilience4j.spring.boot)
}
```

## Команды
```bash
./gradlew :people:bootRun --args='--spring.profiles.active=local'
./gradlew :people:check                         # тесты + checkstyle + jacoco
./gradlew :people:update :people:generateJooq   # после новой миграции
./gradlew :people:bootJar
./gradlew check                                  # всё
```

## Профили
`application.yml` — общее, секреты `${DB_PASSWORD}` без дефолта; `application-local.yml` — localhost-адреса для запуска из IDE; в compose профиль не нужен, адреса приходят env-переменными. Spring Cloud Config и Vault не используем (ADR-027): на один сервер и один день это лишняя точка отказа.

## Dockerfile (общий, из готового jar)
jOOQ генерируется из живой БД, а внутри `docker build` БД нет. Поэтому jar собирает Gradle (CI или `infra/up.sh` локально: поднять postgres → `update generateJooq bootJar` → `compose up --build`), а Dockerfile только упаковывает его слоями.
```dockerfile
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
```

Healthcheck — в compose: `wget -qO- localhost:8081/actuator/health/readiness` (management на отдельном порту 8081, наружу не публикуется). Наличие `wget` в образе проверь один раз на старте; если нет — `apt-get install -y --no-install-recommends wget` в runtime-стадии.

## CI (из sueta, исправлено)
- Detect changes → матрица только по изменённым сервисам; изменение `libs/`, `build-logic/`, `gradle/` → все сервисы; push в main → все.
- **Тесты не пропускаются** (в sueta `skip-tests: true` по умолчанию).
- Для сервисов с jOOQ — `services: postgres` в job, затем `./gradlew :svc:update :svc:generateJooq :svc:check`.
- `gradle/actions/setup-gradle` кэширует сам; ручной `actions/cache` для `~/.gradle` не нужен.
- Отчёты JUnit — `mikepenz/action-junit-report` (как в certis), jacoco-summary в `$GITHUB_STEP_SUMMARY`.
- Docker-образы собираются только после мержа в main и пушатся в GHCR (не в личный Docker Hub).
