# api

Java 17 / Spring Boot REST API for InventoryApp. Scaffolded in INV-3.

## Container image

`Dockerfile` builds the API in two stages: Maven on a Java 17 JDK, then only the jar on a Java 17 JRE, running as a non-root user on port 8080. Tests are not run in the image build; CI runs them first. `.dockerignore` sends only `mvnw`, `.mvn/`, `pom.xml` and `src/main/` to the build.

```bash
docker build -t inventory-api .
```

In AWS the image is built in CloudShell or GitHub Actions and pushed to ECR (INV-15, INV-16); no local Docker is needed.
