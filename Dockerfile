# ETAPA 1: CONSTRUCCIÓN (BUILD STAGE)
# Usamos una imagen de JDK 21 para compilar el código.
FROM openjdk:21-jdk-slim AS build

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia los archivos de configuración de Maven (pom.xml y .mvn) primero.
# Esto optimiza el caché de Docker si solo cambias el código fuente.
COPY pom.xml .
COPY .mvn .mvn

# Copia el código fuente completo
COPY src src

# Ejecuta el comando de construcción para generar el JAR ejecutable.
# La bandera -DskipTests es opcional, pero acelera el deploy.
RUN ./mvnw package -DskipTests

# -------------------------------------------------------------

# ETAPA 2: EJECUCIÓN (RUNTIME STAGE)
# Usamos una imagen de JRE 21 (solo ambiente de ejecución) para un tamaño final mínimo.
FROM openjdk:21-jre-slim

# Establece el puerto en el que la aplicación esperará peticiones.
# Render sobreescribirá esto, pero es una buena práctica incluirlo.
EXPOSE 8080

# Copia el JAR construido de la etapa 'build' al nuevo contenedor 'runtime'.
# El JAR suele encontrarse en el directorio 'target' del contenedor de build.
COPY --from=build /app/target/*.jar app.jar

# Define el comando que se ejecutará al iniciar el contenedor.
# Esto inicia tu aplicación Java.
ENTRYPOINT ["java", "-jar", "app.jar"]