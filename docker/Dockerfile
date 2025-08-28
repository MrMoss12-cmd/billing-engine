# Dockerfile para microservicio billing-engine
FROM openjdk:17-jdk-slim

# Directorio de trabajo en contenedor
WORKDIR /app

# Copiar el archivo JAR compilado (ajusta el nombre según tu build)
COPY build/libs/billing-engine.jar billing-engine.jar

# Exponer puerto HTTP del servicio Spring Boot
EXPOSE 8080

# Comando para iniciar la aplicación
ENTRYPOINT ["java","-jar","billing-engine.jar"]
