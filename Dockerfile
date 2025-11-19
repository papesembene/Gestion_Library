# ===== Étape 1 : Build =====
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Répertoire de travail
WORKDIR /app

# Copier uniquement le pom pour utiliser le cache Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src ./src

# Build le JAR (skip tests en CI pour rapidité)
RUN mvn clean package -DskipTests -B

# ===== Étape 2 : Image finale légère =====
FROM eclipse-temurin:17-jre-jammy

# Créer un utilisateur non-root
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --create-home appuser

# Répertoire de travail
WORKDIR /app

# Copier le jar depuis l’étape build
COPY --from=build /app/target/*.jar /app/app.jar

# Donner les droits à l’utilisateur
RUN chown -R appuser:appgroup /app

# Passer en utilisateur non-root
USER appuser

# Exposer le port de l'application
EXPOSE 8080

# JVM optimisée pour la prod
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# Healthcheck simple pour Kubernetes
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Commande de démarrage
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
