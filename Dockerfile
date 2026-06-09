# =====================================================
# STAGE 1: Build con Maven
# =====================================================
FROM maven:3.9.5-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# =====================================================
# STAGE 2: Imagen final liviana
# =====================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="equipo-transportista"
LABEL app="guias-despacho"

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080
ENV EFS_MOUNT_PATH=/mnt/efs/guias

WORKDIR /app

RUN mkdir -p /mnt/efs/guias

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Xms256m", \
    "-Xmx512m", \
    "-jar", "app.jar"]