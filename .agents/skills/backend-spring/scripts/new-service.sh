#!/usr/bin/env bash
# Новый сервис из шаблона. Запуск из корня Gradle-монорепы (<case>/services: proxodnaya | documents | subsidy).
# Использование: new-service.sh <svc> <port> <Owner> [--no-db|--gateway]
set -euo pipefail
SVC=${1:?usage: new-service.sh <svc> <port> <Owner> [--no-db|--gateway]}; PORT=${2:?port}; OWNER=${3:?owner (B1..B3)}
KIND=${4:-db}; KIND=${KIND#--}
[[ "$SVC" =~ ^[a-z][a-z0-9]*$ ]] || { echo "имя сервиса: одно слово латиницей в нижнем регистре"; exit 1; }
[ -f settings.gradle.kts ] || { echo "запусти из корня Gradle-монорепы"; exit 1; }
# папка может уже существовать с документами модуля (AGENTS.md, CLAUDE.md, docs/) — это не код
{ [ -e "$SVC/build.gradle.kts" ] || [ -e "$SVC/src" ] || [ -e "$SVC/pyproject.toml" ]; } && { echo "$SVC уже существует"; exit 1; }
# кейс берём из rootProject.name = "<case>-services" (init-backend.sh)
PROJECT=$(sed -nE 's/^rootProject.name *= *"([a-z]+)-services".*/\1/p' settings.gradle.kts); PROJECT=${PROJECT:-proxodnaya}
ROOT_PKG=ru.$PROJECT; PKG=$ROOT_PKG.$SVC
CLS="$(tr '[:lower:]' '[:upper:]' <<<"${SVC:0:1}")${SVC:1}"
MAIN=$SVC/src/main/java/$(tr . / <<<"$PKG"); TEST=$SVC/src/test/java/$(tr . / <<<"$PKG")
RES=$SVC/src/main/resources; TRES=$SVC/src/test/resources
mkdir -p "$MAIN" "$TEST" "$RES" "$TRES" "$SVC/http"
TS=$(date -u +%Y%m%d%H%M%S)

# ---------- build.gradle.kts ----------
case "$KIND" in
db)    printf 'plugins {\n    id("proxodnaya.jooq")\n}\n\ndependencies {\n}\n' > "$SVC/build.gradle.kts" ;;
no-db) printf 'plugins {\n    id("proxodnaya.service")\n}\n\ndependencies {\n}\n' > "$SVC/build.gradle.kts" ;;
gateway) printf 'plugins {\n    id("proxodnaya.gateway")\n}\n' > "$SVC/build.gradle.kts" ;;
*) echo "неизвестный вид: $KIND"; exit 1 ;;
esac

# ---------- Application ----------
cat > "$MAIN/${CLS}Application.java" <<EOF
package $PKG;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Сервис $SVC. Владелец: $OWNER.
 */
@SpringBootApplication
public class ${CLS}Application {

    public static void main(String[] args) {
        SpringApplication.run(${CLS}Application.class, args);
    }
}
EOF

# ---------- application.yml ----------
{
cat <<EOF
spring:
  application:
    name: $SVC
  threads:
    virtual:
      enabled: true
EOF
if [ "$KIND" = db ]; then cat <<EOF
  datasource:
    url: jdbc:postgresql://\${DB_HOST}:\${DB_PORT:5432}/$SVC
    username: ${SVC}
    password: \${${SVC^^}_DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    contexts: \${LIQUIBASE_CONTEXTS:prod}
  kafka:
    bootstrap-servers: \${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      properties:
        enable.idempotence: true
    consumer:
      group-id: $SVC
      auto-offset-reset: earliest
      enable-auto-commit: false
EOF
fi
cat <<EOF
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: \${OIDC_ISSUER_URI}
          audiences: $SVC

server:
  port: $PORT
  shutdown: graceful

management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: \${OTLP_ENDPOINT:http://jaeger:4318/v1/traces}

springdoc:
  api-docs:
    path: /v3/api-docs
EOF
} > "$RES/application.yml"

if [ "$KIND" = gateway ]; then
  sed -i '/^springdoc:/,$d' "$RES/application.yml"
  cat >> "$RES/application.yml" <<'EOF'
# Маршруты: /<svc>/api/v1/** → http://<svc>:<port>/api/v1/** (паттерн sueta). JWT проверяет и gateway, и сервис.
#  cloud:
#    gateway:
#      server:
#        webflux:
#          routes:
#            - id: people
#              uri: http://people:8082
#              predicates: [ "Path=/people/api/v1/**" ]
#              filters: [ "RewritePath=/people/(?<path>.*), /$\\{path}" ]
EOF
fi

cat > "$RES/application-local.yml" <<EOF
# Запуск из IDE против infra/docker-compose (порты проброшены только в override для разработки).
EOF
[ "$KIND" = db ] && cat >> "$RES/application-local.yml" <<EOF
DB_HOST: localhost
KAFKA_BOOTSTRAP_SERVERS: localhost:9092
${SVC^^}_DB_PASSWORD: local
spring:
  liquibase:
    contexts: dev
EOF
cat >> "$RES/application-local.yml" <<EOF
OIDC_ISSUER_URI: http://localhost:8180/realms/proxodnaya
OTLP_ENDPOINT: http://localhost:4318/v1/traces
EOF

# ---------- Liquibase ----------
if [ "$KIND" = db ]; then
  mkdir -p "$RES/db/changelog/changesets"
  cat > "$RES/db/changelog/db.changelog-master.yaml" <<'EOF'
databaseChangeLog:
  - includeAll:
      path: changesets/
      relativeToChangelogFile: true
      errorIfMissingOrEmpty: true
EOF
  cat > "$RES/db/changelog/changesets/$TS-outbox-and-inbox.sql" <<EOF
--liquibase formatted sql

--changeset ${OWNER,,}:$TS-outbox-and-inbox
create table outbox (
    id           uuid primary key,
    topic        text        not null,
    message_key  text        not null,
    payload      jsonb       not null,
    created_at   timestamptz not null,
    published_at timestamptz
);
create index outbox_unpublished_idx on outbox (created_at) where published_at is null;

create table processed_events (
    event_id     uuid primary key,
    processed_at timestamptz not null default now()
);
--rollback drop table processed_events; drop table outbox;
EOF
fi

# ---------- tests ----------
cat > "$TRES/archunit.properties" <<'EOF'
archRule.failOnEmptyShould=false
EOF
cat > "$TEST/ArchitectureTest.java" <<EOF
package $PKG;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Правила слоёв из скилла backend-spring. Не удалять и не ослаблять без ADR.
 */
@AnalyzeClasses(packages = "$PKG", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_TOUCH_PERSISTENCE = noClasses()
            .that().resideInAPackage("..api.controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "org.jooq..");

    @ArchTest
    static final ArchRule QUERIES_DO_NOT_CALL_COMMANDS = noClasses()
            .that().resideInAPackage("..query..")
            .should().dependOnClassesThat().resideInAPackage("..command..");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_REPOSITORIES = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule SHARED_IS_INDEPENDENT = noClasses()
            .that().resideInAPackage("$PKG.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("$PKG.features..", "$PKG.api..");

    @ArchTest
    static final ArchRule FEATURES_ARE_FREE_OF_CYCLES = slices()
            .matching("$PKG.features.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule FEATURES_TALK_THROUGH_API_OR_SPI = noClasses()
            .that().resideInAPackage("$PKG.features.(*)..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("$PKG.features.(*).command..", "$PKG.features.(*).query..",
                    "$PKG.features.(*).application..")
            .allowEmptyShould(true)
            .because("другая фича доступна только через свой api/spi; правило уточняет be-check.mjs");

    @ArchTest
    static final ArchRule KAFKA_TEMPLATE_ONLY_IN_OUTBOX = noClasses()
            .that().resideOutsideOfPackage("..messaging.outbox..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate");

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
}
EOF
# FEATURES_TALK_THROUGH_API_OR_SPI в ArchUnit без группового сравнения пропускает «свою» фичу неточно —
# поэтому правило выключено, а проверку делает be-check.mjs (сравнивает имя фичи-источника и цели).
python3 - "$TEST/ArchitectureTest.java" <<'PY'
import sys,re; p=sys.argv[1]; s=open(p).read()
s=re.sub(r'\n    @ArchTest\n    static final ArchRule FEATURES_TALK_THROUGH_API_OR_SPI.*?;\n','',s,flags=re.S)
open(p,'w').write(s)
PY

if [ "$KIND" = db ]; then
  mkdir -p "$TEST/config" "$TEST/integrations"
  cat > "$TEST/config/AbstractIntegrationTest.java" <<EOF
package $PKG.config;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * База для всех *IntegrationTest: Postgres и Kafka в Testcontainers, общие зависимости — protected.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
EOF
  cat > "$TEST/integrations/ApplicationContextIntegrationTest.java" <<EOF
package $PKG.integrations;

import $PKG.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/v1/does-not-matter")).andExpect(status().isUnauthorized());
    }
}
EOF
  cat > "$TEST/integrations/OpenApiContractIntegrationTest.java" <<EOF
package $PKG.integrations;

import $PKG.config.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Выгружает OpenAPI в contracts/openapi/$SVC.json (gradle :$SVC:exportOpenApi). CI сверяет git diff.
 */
class OpenApiContractIntegrationTest extends AbstractIntegrationTest {

    @Test
    void openApi_isServedAndExported() throws Exception {
        // given
        String dir = System.getProperty("contract.dir");

        // when
        String json = mockMvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        if (dir != null) {
            Object tree = objectMapper.readValue(json, Object.class);
            Files.createDirectories(Path.of(dir));
            Files.writeString(Path.of(dir, "$SVC.json"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\\n");
        }
    }
}
EOF
  cat > "$TRES/application-test.yml" <<'EOF'
spring:
  liquibase:
    contexts: test
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ""
          jwk-set-uri: http://localhost:0/jwks
management:
  tracing:
    enabled: false
EOF
fi

cat > "$SVC/http/$SVC.http" <<EOF
### $SVC: health (management-порт, внутри сети)
GET http://localhost:8081/actuator/health

### $SVC: пример через gateway (токен из Keycloak: см. http/auth.http)
GET {{gateway}}/$SVC/api/v1/...
Authorization: Bearer {{token}}
EOF

echo "Создан $SVC ($KIND, порт $PORT, владелец $OWNER). Дальше вручную:"
echo " - маршрут в gateway (application.yml), контейнер и БД в infra/, client '$SVC' в realm Keycloak"
echo " - владелец в .github/CODEOWNERS, строка в $PROJECT/AGENTS.md"
[ "$PROJECT" != proxodnaya ] && grep -rlI 'proxodnaya' "$SVC" | xargs -r sed -i "s/proxodnaya/$PROJECT/g"
true
