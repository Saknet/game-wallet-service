# Build Stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
# Use 'gradle' directly since 'gradlew' wrapper script is missing
RUN gradle bootJar --no-daemon -x test

# Run Stage
FROM amazoncorretto:17
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Copy the keystore
COPY src/main/resources/keystore.p12 /app/keystore.p12

EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]