# ---- Build stage ----
FROM maven:3.9.1-eclipse-temurin-17 AS build
WORKDIR /workspace
ARG JAR_FILE
COPY ${JAR_FILE} /workspace/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
