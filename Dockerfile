# ---- Build stage ----
FROM maven:3.9.1-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B clean package -DskipTests
RUN cp target/*.jar /workspace/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
