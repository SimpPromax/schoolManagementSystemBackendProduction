FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Create necessary directories for uploads and logs
RUN mkdir -p /app/uploads /app/logs

COPY target/SchoolManagementSystem-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]