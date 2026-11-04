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

Spring Boot starts Postgres from `compose.yaml`. The API runs on `http://localhost:8080`:

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

## Deploying

_To be completed in INV-16 / INV-24._

## Ways of working

- Never commit directly to `main`. Every change goes through a feature branch and a pull request.
- Branch names: `feature/INV-<n>-short-description` (or `fix/`, `chore/`).
- Commit messages: `INV-<n>: Short imperative summary`, for example `INV-12: Add reorder-level validation`.
- No passwords, keys or tokens in code, commits or screenshots.

## Documentation

- [Design note](docs/design-note.md)
- [Backlog](docs/backlog.md)
