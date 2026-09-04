# ---- build stage (Maven + JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S crm && adduser -S crm -G crm
WORKDIR /app
RUN mkdir -p /app/data/storage && chown -R crm:crm /app
COPY --from=build /app/target/*.jar app.jar
USER crm
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
