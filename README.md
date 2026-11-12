# InventoryApp

A web application where staff track stock levels, record stock movements, import items from CSV, and get alerted when an item falls to its reorder level. It runs on AWS and is deployed by a GitHub Actions pipeline.

## Repository layout

| Path | Contents |
|---|---|
| `api/` | Java 17 / Spring Boot REST API (PostgreSQL, JWT, OpenAPI) |
| `web/` | React front end |
| `infra/` | AWS infrastructure (CloudFormation templates, Lambda functions) |
| `docs/` | Design note, backlog, architecture diagram, lessons learned |
| `.github/` | CI/CD workflows and PR template |

## Tech stack

- **Back end:** Java 17, Spring Boot, PostgreSQL, Flyway, Spring Security (JWT), springdoc-openapi
- **Front end:** React (functional components and hooks), React Router
- **AWS:** ECS Fargate, ALB, ECR, RDS, S3, CloudFront, SQS, SNS, Lambda, API Gateway, DynamoDB, Secrets Manager, KMS, CloudWatch, CloudTrail, CloudFormation, IAM
- **CI/CD:** GitHub Actions with OIDC to AWS

## Prerequisites

- Java 17 (Temurin)
- Node.js and npm (for `web/`)
- A Docker runtime. On macOS we use [Colima](https://github.com/abiosoft/colima) instead of Docker Desktop:
  ```bash
  brew install colima docker && colima start
  ```
  Then add these to `~/.zshrc` so Testcontainers and Spring Boot find Colima:
  ```bash
  export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  ```

## Running locally

### API (`api/`)

The first Admin is created at startup from two environment variables (only if no Admin exists yet). Choose your own values; never commit them.

```bash
cd api
export INVENTORY_ADMIN_USERNAME=admin
export INVENTORY_ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

Spring Boot starts Postgres and S3Mock (local S3 for item images, port 9090) from `compose.yaml`. The API runs on `http://localhost:8080`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- Postman: import the files in `api/postman/`

Run the tests (Postgres is started by Testcontainers):

```bash
./mvnw verify
```

### Web (`web/`)

Start the API first, then:

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:5173` and sign in with the Admin from `INVENTORY_ADMIN_USERNAME` / `INVENTORY_ADMIN_PASSWORD`. Vite forwards `/api/*` to `http://localhost:8080`; set `VITE_API_URL` to use another address.

To work on the UI without the API, run `npm run dev:mock`. It answers `/api/*` from fake data in `src/mocks/handlers.js` (users `admin/admin123`, `staff/staff123`, `viewer/viewer123`).

Run the tests and checks:

```bash
npm test
npm run lint
npm run build
```

## Quality gate (SonarCloud)

Every pull request is analysed by [SonarCloud](https://sonarcloud.io) using the default "Sonar way" quality gate, which needs at least 80% test coverage on new code. CI uploads the coverage from `./mvnw verify` (JaCoCo) and `npm run test:coverage` (Vitest), and the **SonarCloud - quality gate** check fails if the gate fails.

Run the same coverage locally:

```bash
cd api && ./mvnw verify            # api/target/site/jacoco/index.html
cd web && npm run test:coverage    # web/coverage/lcov-report/index.html
```

One-time setup (already done for this repository; needed again only for a fork):

1. Sign in at sonarcloud.io with GitHub and import this repository (free for public repositories).
2. In the SonarCloud project, turn off **Automatic Analysis** (Administration → Analysis Method), because CI runs the analysis.
3. Create a token (My Account → Security) and add it to the GitHub repository as the secret `SONAR_TOKEN` (Settings → Secrets and variables → Actions).
4. Check that `sonar.organization` and `sonar.projectKey` in `sonar-project.properties` match the SonarCloud project.

## Deploying

The app runs in AWS (`eu-north-1`) at the `WebUrl` output of the `inventory-frontend` stack (`https://<id>.cloudfront.net`). CloudFront serves the React build from S3 and sends `/api/*` to the API behind the load balancer.

When CI passes on `main`, `.github/workflows/deploy.yml` waits for approval in the `sandbox` environment. It then rolls out the API image, publishes the web app and smoke-tests both. The stacks and the order to create them in are in [infra/README.md](infra/README.md).

## Ways of working

- Never commit directly to `main`. Every change goes through a feature branch and a pull request.
- Branch names: `feature/INV-<n>-short-description` (or `fix/`, `chore/`).
- Commit messages: `INV-<n>: Short imperative summary`, for example `INV-12: Add reorder-level validation`.
- No passwords, keys or tokens in code, commits or screenshots.

## Documentation

- [Design note](docs/design-note.md)
- [Backlog](docs/backlog.md)
