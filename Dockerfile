# One image that serves both the Angular app and the API from a single address.
#
#   docker build -t salary-management .
#   docker run -p 8080:8080 -e JWT_SECRET="at-least-32-random-characters-here" -e AUTH_COOKIE_SECURE=false salary-management
#
# Then open http://localhost:8080 and sign in with a demo account (see README).
# AUTH_COOKIE_SECURE=false is only for plain http on your own machine; behind HTTPS leave it out.

# ---- 1. Build the Angular app ----
FROM node:22-alpine AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2. Build the Spring Boot jar, with the built Angular files inside it ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/ ./
COPY --from=ui /ui/dist/frontend/browser/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package

# ---- 3. Small runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/salary-management-*.jar app.jar

# The database is a single file. Mount a volume on /data to keep changes across restarts; without one, the data is
# recreated from the bundled demo data each time the container starts.
ENV DB_URL=jdbc:sqlite:/data/salary.db \
    SEED_EMPLOYEES=true \
    SEED_DEMO_USERS=true
VOLUME /data
EXPOSE 8080

# JWT_SECRET (at least 32 characters) must be provided when the container starts; there is deliberately no default.
ENTRYPOINT ["java", "-jar", "app.jar"]
