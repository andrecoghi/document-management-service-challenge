# syntax=docker/dockerfile:1

FROM openjdk:17-jdk-slim AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM openjdk:17-jdk-slim AS runtime
ENV JAVA_OPTS="-Xms50m -Xmx50m"
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
