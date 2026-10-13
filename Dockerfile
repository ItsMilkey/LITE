# --- Etapa 1: Construcción (Build Stage) ---
# Se mantiene igual, construye la aplicación
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests


# --- Etapa 2: Ejecución (Run Stage) ---
# Aquí aplicamos la solución
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia el JAR ya compilado desde la etapa de construcción
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]