# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Baixa as dependências numa camada separada: se só o código mudar, o Docker reaproveita esta.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home spring
USER spring

# Camadas da menos para a mais alterada, para builds e deploys mais rápidos.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
