FROM openjdk:17.0.1-slim

WORKDIR /app
EXPOSE 8082

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests

ENTRYPOINT ["java","-jar","/app/target/importer-service-0.0.1-SNAPSHOT.jar"]
