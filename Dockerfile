# syntax=docker/dockerfile:1

# One image per module: docker build --build-arg MODULE=backend (or frontend) .
ARG MODULE=backend

# Build stage: compiles and tests the module with a full JDK. The Maven cache survives between builds.
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Maven reads the POMs of all modules, but only builds the one asked for.
COPY backend/pom.xml backend/
COPY frontend/pom.xml frontend/
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl ${MODULE} package

# Run stage: only the Java runtime and the application jar.
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
