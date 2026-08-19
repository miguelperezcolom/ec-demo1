# Runtime-only image: the jar is built outside (see deploy/build-shell.sh), because building
# Maven inside the image would re-resolve the whole dependency tree on every change.
#
# Non-root and writing only to /tmp, which is what the Deployment's runAsNonRoot +
# readOnlyRootFilesystem require.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /opt/app
COPY --chown=app:app target/shell-*.jar app.jar
ENV SERVER_PORT=8101 JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp
USER app
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "app.jar"]
