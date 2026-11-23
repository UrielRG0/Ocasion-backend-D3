# Etapa 1: Compilar y empaquetar el JAR
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw package -DskipTests

# Etapa 2: Ejecutar el JAR en una imagen ligera
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]