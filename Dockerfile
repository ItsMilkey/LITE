# --- Etapa 1: Construcción (Build Stage) ---
# CORRECCIÓN: Usamos una imagen de Maven que incluye Java 21 (temurin-21).
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests


# --- Etapa 2: Ejecución (Run Stage) ---
# CORRECCIÓN: Usamos una imagen de JRE que también es Java 21.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]