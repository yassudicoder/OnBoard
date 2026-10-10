# OnBoard backend as a Docker image, for Render (render.yaml) or any Docker host.
#
# Why build from the repo ROOT (not backend/)? The backend compiles two shared plain-Java folders that sit next to it
# (recovery-core, routing-core) and copies the demo data from mock-data/ into its jar (see backend/pom.xml), so the
# build needs all four folders.
#
# Try it on your laptop (Docker Desktop running, from the repo root):
#   docker build -t onboard-backend .
#   docker run --rm -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/safarsaathi onboard-backend

# ---- Stage 1: build the jar (Maven 3.9.16 = the version the Maven Wrapper pins, on JDK 21) ----
FROM maven:3.9.16-eclipse-temurin-21-noble AS build
WORKDIR /src
# The pom first, so Docker can reuse the downloaded dependencies when only Java files change.
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY recovery-core recovery-core
COPY routing-core routing-core
COPY mock-data mock-data
# Tests run on our laptops and in CI (./mvnw test); skipping them here keeps a deploy to a few minutes.
RUN mvn -B -q -f backend/pom.xml -DskipTests package

# ---- Stage 2: run it (Java runtime only, no build tools) ----
FROM eclipse-temurin:21.0.12_8-jre-noble
WORKDIR /app
COPY --from=build /src/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
# Render's free plan gives 512 MB of memory. These flags keep Java inside it:
#   MaxRAMPercentage=70 = the heap may use 70% of the container's memory, the rest is for Java itself;
#   SerialGC and small thread stacks = less memory per thread; TieredStopAtLevel=1 = faster start on a slow CPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
# A hosted server must listen on every network address; Render tells the app its port through PORT.
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
