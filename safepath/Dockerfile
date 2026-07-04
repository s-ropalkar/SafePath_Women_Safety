FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY config ./config
COPY frontend ./frontend

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/safepath-1.0.0.jar app.jar
COPY --from=build /app/config ./config
COPY --from=build /app/frontend ./frontend

EXPOSE 8080

CMD ["java","-jar","app.jar"]