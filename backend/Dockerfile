# ─── build stage ─────────────────────────────────────────────────────
FROM gradle:8.10-jdk21 AS build
WORKDIR /app

# Copy build files first for layer caching
COPY build.gradle settings.gradle gradle.properties ./

# Copy sources
COPY src src

# Build fat jar (Spring Boot bootJar)
RUN gradle bootJar --no-daemon

# ─── runtime stage ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8000
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
