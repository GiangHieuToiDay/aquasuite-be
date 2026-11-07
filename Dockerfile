FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
# Bỏ -q để hiển thị log tải thư viện
RUN mvn -B dependency:go-offline

COPY src ./src
# Bỏ -q để hiển thị log compile/build package
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
