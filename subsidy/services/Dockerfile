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
