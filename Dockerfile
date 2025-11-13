# --- Etapa 1: Construcción (Build Stage) ---
# Usamos una imagen oficial de Maven con Java 17 para construir el proyecto.
FROM maven:3.8.5-openjdk-17 AS build

# Establecemos el directorio de trabajo dentro del contenedor.
WORKDIR /app

# Copiamos solo el archivo pom.xml para aprovechar el caché de Docker.
# Si las dependencias no cambian, Docker no las volverá a descargar.
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiamos el resto del código fuente.
COPY src ./src

# Empaquetamos la aplicación en un archivo .jar, omitiendo los tests.
# Esto genera el archivo JAR en el directorio /app/target/
RUN mvn package -DskipTests


# --- Etapa 2: Ejecución (Run Stage) ---
# Usamos una imagen mucho más ligera que solo contiene Java para ejecutar la app.
FROM eclipse-temurin:17-jre-alpine

# Establecemos el directorio de trabajo.
WORKDIR /app

# Copiamos el archivo .jar que compilamos en la etapa anterior.
COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto 8080 para que Render pueda redirigir el tráfico hacia él.
EXPOSE 8080

# El comando que se ejecutará cuando el contenedor se inicie.
# Le decimos a Java que ejecute nuestro archivo .jar.
ENTRYPOINT ["java", "-jar", "app.jar"]
