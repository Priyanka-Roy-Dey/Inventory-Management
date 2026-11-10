# InventoryApp API image (INV-15). Build from the api/ folder: docker build -t inventory-api .
# Tests are not run here; CI runs them with Testcontainers before an image is built.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Dependencies first, so they are cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true -Djacoco.skip=true \
    && cp target/inventory-api-*.jar app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

EXPOSE 8080
# Size the heap from the task's memory limit instead of the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
