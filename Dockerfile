# ---- 1) build the React frontend ----
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2) build the Spring Boot jar (frontend is served from /static inside the jar) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# ---- 3) slim runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/beanstalk-comic-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true"
EXPOSE 8080
# Render sets $PORT; application.properties reads it.
ENTRYPOINT ["java", "-jar", "app.jar"]
