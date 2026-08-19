# Runtime-only image from a locally-built jar; see deploy/build-images.sh.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /opt/app
COPY --chown=app:app target/gateway-*.jar app.jar
ENV SERVER_PORT=8190 JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp
USER app
EXPOSE 8190
ENTRYPOINT ["java", "-jar", "app.jar"]
