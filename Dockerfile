# ETAPA 1: CONSTRUCCIÓN (BUILD STAGE)
# Usamos la imagen JDK 21 slim-buster (una etiqueta estable y ligera)
FROM openjdk:21-jdk-slim-buster AS build

# Establece el directorio de trabajo
WORKDIR /app

# Copia los archivos de configuración de Maven
COPY pom.xml .
COPY .mvn .mvn

# Copia el código fuente completo
COPY src src

# Ejecuta el comando de construcción para generar el JAR ejecutable.
# Se usa ./mvnw package para generar el JAR final
RUN ./mvnw package -DskipTests

# -------------------------------------------------------------

# ETAPA 2: EJECUCIÓN (RUNTIME STAGE)
# Usamos la imagen JRE 21 slim-buster para un tamaño final mínimo.
FROM openjdk:21-jre-slim-buster

# Expone el puerto (Render usará la variable PORT automáticamente, pero es una buena práctica)
EXPOSE 8080

# Define el nombre exacto del archivo JAR que se generó en la etapa de construcción:
ARG JAR_FILE=target/saveup-0.0.1-SNAPSHOT.jar

# Copia el JAR construido y lo renombra a app.jar
COPY --from=build /app/${JAR_FILE} app.jar

# Comando de Inicio: ejecuta la aplicación Java
ENTRYPOINT ["java", "-jar", "app.jar"]