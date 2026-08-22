# Gargantua Control Plane — self-contained multi-stage image.
#
# The shared agent-core is built FROM SOURCE here (stage `agentcore`) so the image needs
# no Maven Central release of agent-core — important while the model is on a -SNAPSHOT.
# The Runtime repo is supplied as a named build context `runtime_src` (see the compose
# file: `additional_contexts: { runtime_src: ../gargantua }`). To build this repo
# standalone, pass it yourself:
#   docker build --build-context runtime_src=../gargantua -t gargantua-control-plane .

# 1) Build + install agent-core (and the parent POM) into a local Maven repo.
FROM maven:3.9-eclipse-temurin-25-alpine AS agentcore
WORKDIR /ac
COPY --from=runtime_src pom.xml ./pom.xml
COPY --from=runtime_src agent-core ./agent-core
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
# Profiles (postgres, minio) and connection details are supplied by Compose via env.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
