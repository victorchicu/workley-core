# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies || true

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test && \
    cp build/libs/*.jar build/app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 1001 spring && \
    useradd  --system --uid 1001 --gid spring spring

COPY --from=build --chown=spring:spring /workspace/build/app.jar app.jar

USER spring:spring
EXPOSE 8443

ENTRYPOINT ["java", "-jar", "/app/app.jar"]