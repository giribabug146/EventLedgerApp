FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY account-service/pom.xml account-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY account-service/src account-service/src
COPY gateway-service/src gateway-service/src
RUN mvn -pl gateway-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/gateway-service/target/gateway-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
