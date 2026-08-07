# Gargantua Control Plane — self-contained multi-stage image.
#
# agent-core (io.github.giskardb:agent-core) is resolved from Maven Central, so this
# build needs nothing but this repo as its context — no sibling Runtime checkout.
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -q
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -q

# Glibc-based JRE (Ubuntu Jammy) for parity with the other Gargantua JVM images.
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
