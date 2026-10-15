# syntax=docker/dockerfile:1

# Build stage: compiles and tests with a full JDK. The Maven cache survives between builds.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package

# Run stage: only the Java runtime and the application jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/homepage-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
