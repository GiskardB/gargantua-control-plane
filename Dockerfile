# Gargantua Control Plane — self-contained multi-stage image.
#
# The shared agent-core is built FROM SOURCE here (stage `agentcore`) so the image needs
# no Maven Central release of agent-core — important while the model is on a -SNAPSHOT.
# The Runtime repo is supplied as a named build context `runtime_src` (see the compose
# file: `additional_contexts: { runtime_src: ../gargantua }`). To build this repo
# standalone, pass it yourself:
#   docker build --build-context runtime_src=../gargantua -t gargantua-control-plane .

# 1) Build + install agent-core (and the parent POM) into a local Maven repo.
#    The full Runtime repo is copied (not just agent-core/) because `mvn -N install`
#    validates the reactor's declared <modules> exist on disk even in non-recursive
#    mode — the Runtime repo's .dockerignore keeps this cheap (excludes target/, .git).
FROM maven:3.9-eclipse-temurin-25-alpine AS agentcore
WORKDIR /ac
COPY --from=runtime_src . .
RUN mvn -q -N install && mvn -q -pl agent-core install -DskipTests

# 2) Build the Control Plane against that locally-built agent-core.
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build
COPY --from=agentcore /root/.m2/repository/io/github/giskardb /root/.m2/repository/io/github/giskardb
COPY pom.xml .
COPY src src
RUN mvn -q clean package -DskipTests

# 3) Glibc-based JRE (Ubuntu Jammy) runtime.
FROM eclipse-temurin:25-jre-jammy
# curl is used by the Compose healthcheck (and handy for debugging).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app app
USER app
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
# The `postgres` profile and connection details are supplied by Compose via env.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
EXPOSE 8080
# `exec` makes the JVM PID 1 so it receives SIGTERM: without it the signal stops at the
# shell, Spring never shuts down gracefully, and `docker stop` waits out its full grace
# period before killing the process.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
