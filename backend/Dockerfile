# Stage 1: Build file JAR
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
# Caching dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source và build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run application
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Copy file jar from stage build
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]