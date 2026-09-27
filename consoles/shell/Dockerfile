# Runtime-only image from a locally-built jar; see deploy/build-images.sh, which builds this module
# twice — once per renderer — and this image from each.
#
# Non-root and writing only to /tmp, which is what the Deployment's runAsNonRoot +
# readOnlyRootFilesystem require. The port is in the jar (application.yaml, set by the renderer's
# Maven profile — 8101/8111 Vaadin, 8102/8112 Redwood); the manifests also set SERVER_PORT.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /opt/app
COPY --chown=app:app target/shell-*.jar app.jar
ENV JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
