FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r siga && useradd -r -g siga siga
COPY --from=build /app/target/*.jar app.jar
USER siga
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
