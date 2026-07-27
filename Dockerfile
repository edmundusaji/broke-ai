# Build using maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom and folder
COPY pom.xml .
COPY src ./src
# Maven clean
RUN mvn clean package -DskipTests

# Java JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Rename to app.jar
COPY --from=build /app/target/*.jar app.jar

# Standard port springboot
EXPOSE 8080

# Primary execution
ENTRYPOINT ["java", "-jar", "app.jar"]