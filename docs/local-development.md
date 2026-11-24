# Local development

How to run the API and the web app on your own machine, how to run every test suite, and which environment variables each part reads. Nothing here needs an AWS account.

## 1. Prerequisites

| Tool | Version | Used for |
|---|---|---|
| Java (Temurin) | 17, as in CI | The API (`api/`). Maven comes with the project as `./mvnw`. |
| Node.js and npm | 22, as in CI | The web app (`web/`) |
| A Docker runtime | Any | Postgres and S3Mock for the API, and Testcontainers in the API tests |
| Python | 3.12, as in CI | Only for the alert recorder Lambda tests and the performance seed |

On macOS we use [Colima](https://github.com/abiosoft/colima) instead of Docker Desktop:

```bash
brew install colima docker
```

```bash
colima start
```

Then add these lines to `~/.zshrc` so that Spring Boot and Testcontainers can find Colima, and open a new terminal:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Check that Docker answers with `docker ps`. After a restart, run `colima start` again before starting the API or its tests.

## 2. Run the back end (`api/`)

```bash
cd api
export INVENTORY_ADMIN_USERNAME=admin
export INVENTORY_ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

What happens at startup:

1. **Containers.** Spring Boot reads `api/compose.yaml` and starts two containers, then connects to them:
   - **Postgres 17**, the database `inventory`. Its throwaway local credentials are in `compose.yaml`, and Spring Boot picks them up by itself.
   - **S3Mock** on port 9090, a stand-in for S3 for item images, with the bucket `inventory-images`.
2. **Schema.** Flyway creates or updates the schema (`db/migration`, V1 onwards). Hibernate only checks that the entities match it (`ddl-auto=validate`).
3. **First Admin.** If no Admin exists yet, one is created from `INVENTORY_ADMIN_USERNAME` and `INVENTORY_ADMIN_PASSWORD`. Choose your own values and never commit them. Once the Admin exists, these variables are ignored, so changing them later does not change the password.
4. **Signing key.** Without `INVENTORY_JWT_PRIVATEKEY`, a new RSA key is generated at each start. Restarting the API therefore signs out everyone.

The API then listens on `http://localhost:8080`:

| What | Where |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| JWKS (public signing key) | http://localhost:8080/.well-known/jwks.json |
| Postman | Import both files in `api/postman/`, then set `username` and `password` in the **InventoryApp local** environment |

The data stays in the Postgres container between runs. To start with an empty database, stop the API and remove the containers:

```bash
docker compose -f compose.yaml down
```

**Without Docker Compose:** `./mvnw spring-boot:test-run` starts the API with the Testcontainers setup the tests use (`TestInventoryApiApplication`). Its database is new on every start.

## 3. Run the front end (`web/`)

Start the API first, then, in a second terminal:

```bash
cd web
npm install
npm run dev
```

Open http://localhost:5173 and sign in with the Admin from step 2. The first sign-in with a temporary password goes to the change-password page.

Vite forwards every `/api/*` request to the API (`src/dev/devApi.js`). The browser sees one origin, as with CloudFront in AWS, so no CORS setup is needed.

### Without the API

```bash
npm run dev:mock
```

This answers `/api/*` from fake data in `src/mocks/handlers.js`. No Java or Docker is needed. You can sign in as:

- `admin` / `admin123`
- `staff` / `staff123`
- `viewer` / `viewer123`

These users exist only in the mock and are reset when the page reloads.

### Production build

```bash
npm run build
```

```bash
npm run preview
```

`build` writes the app to `web/dist/`; `preview` serves that build at http://localhost:4173. The preview server doesn't forward `/api`, so use it only to check that the build loads.

## 4. Run the tests

### API: unit and integration tests

Docker must be running: Testcontainers starts its own Postgres and S3Mock, separate from the ones in step 2.

```bash
cd api
./mvnw verify
```

This runs every test, then writes the JaCoCo coverage report to `api/target/site/jacoco/index.html`.

To run one test class, or one test method:

```bash
./mvnw test -Dtest=StockMovementTests
```

```bash
./mvnw test -Dtest='StockMovementTests#anOversizedStockOutIsRejectedAndChangesNothing'
```

### Web: component tests, lint and build

```bash
cd web
npm test
```

```bash
npm run lint
```

```bash
npm run test:coverage
```

- `npm test` runs the Vitest and Testing Library tests once. `npx vitest` keeps them running and re-runs them on every save.
- `npm run test:coverage` also writes `web/coverage/lcov-report/index.html`.
- `npm run build` (section 3) is also a CI check: it fails on a broken import.

### Alert recorder Lambda

The Lambda's code is inline in `infra/alerts.yaml`; the test loads it from there. Run from the repository root:

```bash
python3 -m unittest discover -s infra/tests -v
```

### What CI runs

A pull request can merge only when these pass ([ci.yml](../.github/workflows/ci.yml)):

| Check | Command |
|---|---|
| Web - lint, test, build | `npm ci`, `npm run lint`, `npm run test:coverage`, `npm run build` |
| API - test and build | `./mvnw -B verify` |
| Lambda tests | `python -m unittest discover -s infra/tests -v` |
| SonarCloud - quality gate | At least 80% coverage on new code, from the two coverage reports |

Running the first three locally before pushing catches almost every failure.

### Load test (optional)

The k6 test runs against a deployed environment, not locally. See [performance.md](performance.md).

## 5. Environment variables

Spring Boot maps an environment variable to a property by upper-casing it and replacing dots with underscores. For example, `INVENTORY_ADMIN_USERNAME` sets `inventory.admin.username`. Any property in `application.properties` can be overridden the same way.

### API: local

| Variable | Needed? | Default | What it does |
|---|---|---|---|
| `INVENTORY_ADMIN_USERNAME` | Yes, on the first start | none | Username of the first Admin. Used only when no Admin exists. |
| `INVENTORY_ADMIN_PASSWORD` | Yes, on the first start | none | That Admin's password. |
| `INVENTORY_JWT_PRIVATEKEY` | No | A new key at each start | RSA private key, PEM PKCS#8, for signing access tokens. Set it to keep sign-ins valid across restarts. |
| `INVENTORY_JWT_ACCESSTOKENTTL` | No | `15m` | Access token lifetime |
| `INVENTORY_JWT_REFRESHTOKENTTL` | No | `7d` | Refresh token lifetime |
| `INVENTORY_IMAGES_ENDPOINT` | No | `http://localhost:9090` (S3Mock) | S3 address for item images. Empty means real AWS S3. |
| `INVENTORY_IMAGES_BUCKET` | No | `inventory-images` | Image bucket |
| `INVENTORY_IMAGES_MAXSIZE` | No | `2MB` | Largest image upload |
| `INVENTORY_ALERTS_TOPICARN` | No | none | SNS topic for low-stock alerts. Without it nothing is published, and alerts still show on the Alerts page. |
| `DOCKER_HOST`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | With Colima | none | Where Docker is (section 1) |

The database needs no variables locally: Spring Boot takes the connection from `compose.yaml`, and the tests take it from Testcontainers.

To create a signing key and use it in the current terminal (the key file is ignored by Git through `*.pem`):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
```

```bash
export INVENTORY_JWT_PRIVATEKEY="$(cat jwt-private.pem)"
```

### API: in AWS

ECS sets these on each task ([infra/service.yaml](../infra/service.yaml)). They are listed so you know what the deployed API reads; you don't set them locally.

| Variable | Source |
|---|---|
| `SPRING_PROFILES_ACTIVE=aws` | Environment. Loads `application-aws.properties`, which empties the image endpoint so the API uses real S3. |
| `SPRING_DATASOURCE_URL` | Environment, the RDS address |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Secrets Manager |
| `INVENTORY_ADMIN_USERNAME`, `INVENTORY_ADMIN_PASSWORD` | Secrets Manager |
| `INVENTORY_JWT_PRIVATEKEY` | Secrets Manager |
| `INVENTORY_IMAGES_BUCKET`, `INVENTORY_IMAGES_REGION` | Environment |
| `INVENTORY_ALERTS_TOPICARN`, `INVENTORY_ALERTS_REGION` | Environment |

AWS credentials come from the task role, never from variables.

### Web

| Variable | Needed? | Default | What it does |
|---|---|---|---|
| `VITE_API_URL` | No | `http://localhost:8080` | Where `npm run dev` forwards `/api/*`, for example an API on another port |

Set it for one run, or put it in `web/.env.local`, which Git ignores:

```bash
VITE_API_URL=http://localhost:8081 npm run dev
```

The production build needs no variables: it calls `/api` on the same origin, and CloudFront forwards that to the API.

## 6. Common problems

| Symptom | Cause and fix |
|---|---|
| `Could not find a valid Docker environment` in the tests, or `Unable to start docker compose` | Docker isn't running or can't be found. Run `colima start`, check the two variables from section 1 in a new terminal, then run `docker ps`. |
| `Port 9090` or `5432 already in use` | Another container or app uses the port. Run `docker ps`, then stop the old container. |
| Sign-in works, then every request fails after an API restart | The API generated a new signing key. Sign in again, or set `INVENTORY_JWT_PRIVATEKEY`. |
| The Admin password from your variables doesn't work | The Admin was created earlier with other values. Use those, reset the database with `docker compose -f compose.yaml down`, or reset the password as another Admin. |
| The web app shows network errors | The API isn't running on port 8080. Start it, or use `npm run dev:mock`. |
| `Flyway` validation error at startup | A migration that already ran was edited. Never edit an applied migration; add a new one. Locally, reset the database. |
