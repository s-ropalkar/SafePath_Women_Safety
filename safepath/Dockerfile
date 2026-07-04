# SafePath AI — production image for Render (Docker runtime)
# Build context: safepath/ (Render Root Directory)

FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY config ./config
COPY frontend ./frontend

# No exec-maven-plugin / stop-server hooks — safe for CI and Docker
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV SAFEPATH_ROOT=/app

COPY --from=build /app/target/safepath-1.0.0.jar app.jar
COPY --from=build /app/config ./config
COPY --from=build /app/frontend ./frontend

EXPOSE 8080

# Render sets PORT at runtime; AppConfig.serverPort() reads it
CMD ["java", "-jar", "app.jar"]
