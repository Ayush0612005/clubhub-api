# ---- build: compile and package with the Maven wrapper (tests run in CI, not here)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && cp target/clubhub-api-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination /layers

# ---- runtime: JRE only, non-root, one Docker layer per Spring Boot layer so a code change
#      re-pushes only the small "application" layer, not ~100 MB of dependencies
FROM eclipse-temurin:25-jre
RUN groupadd --system clubhub && useradd --system --gid clubhub --home /app clubhub
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER clubhub
EXPOSE 8080 8081
# container-aware heap sizing; UTC so timestamps match the tests and the database
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
# the JRE image ships without curl/wget; bash's /dev/tcp is enough for one HTTP GET
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${MANAGEMENT_SERVER_PORT:-${PORT:-8080}} && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
ENTRYPOINT ["java", "-jar", "app.jar"]
