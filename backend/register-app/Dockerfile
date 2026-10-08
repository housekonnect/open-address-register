# Runtime image of the register backend.
#
# The jar is built on the host (`make build` / `make up`) because jOOQ code generation needs Docker for
# Testcontainers. Keep the Java version identical to .sdkmanrc and maven.compiler.release in backend/pom.xml.
FROM eclipse-temurin:25.0.4_7-jre

# Apply pending OS security updates of the base image (Trivy fails the build on fixable HIGH/CRITICAL findings).
RUN apt-get update \
    && apt-get -y upgrade --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system register && useradd --system --gid register --no-create-home register
WORKDIR /app
COPY --chown=register:register target/register-app.jar /app/register-app.jar

USER register
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/register-app.jar"]
