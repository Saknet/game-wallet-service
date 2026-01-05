# Build Stage (Java 25)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy only Gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./

# Download dependencies (cache layer)
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Copy the rest of the project
COPY . .

# Build jar (skip tests in image build)
RUN ./gradlew --no-daemon clean bootJar -x test

# Run Stage (Java 25)
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar
COPY src/main/resources/keystore.p12 /app/keystore.p12

EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]