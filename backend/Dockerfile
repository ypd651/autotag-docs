# Multi-stage Dockerfile para Spring Boot Backend
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cachear dependencias Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Compilar código fuente
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para seguridad en producción
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Crear directorio de subida persistente
RUN mkdir -p /shared-storage/uploads && chown -R appuser:appgroup /shared-storage

COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
