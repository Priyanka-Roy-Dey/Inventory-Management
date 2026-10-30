# Backlog

One ticket ≈ one PR. Branch: `feature/INV-<n>-short-description`. Commits: `INV-<n>: Short imperative summary`. Tests ship in the same PR as the code.

## Week 1 — Foundations

| Ticket | Feature | Done when |
|---|---|---|
| INV-1 | Repo setup: layout (`api/`, `web/`, `infra/`, `docs/`), README skeleton, `.gitignore`, PR template, design note and backlog | First PR merged |
| INV-2 | CI on PRs (build and test API and web); branch protection with required check and required review | A failing PR cannot merge |
| INV-3 | Spring Boot skeleton: Postgres + Flyway, Docker Compose, consistent error format, OpenAPI/Swagger, Actuator health | `/swagger-ui` and `/actuator/health` work locally |
| INV-4 | Users and auth: users table, BCrypt, RS256 JWT login/refresh, role checks, JWKS endpoint | Login works; Viewer gets 403 on a write endpoint (test) |
| INV-5 | Seed first Admin, user management, temporary-password reset, forced password change | Tests cover seed and must-change flow |
| INV-6 | Items CRUD: validation, SKU 409, Admin-only soft delete, `@Version` | Integration tests cover each rule |
| INV-7 | Static analysis and coverage gate (≥ 70% on new code) | Quality gate shows on PRs |
| INV-8 | React skeleton: router, auth context, protected routes, login page, API client | Can log in and see an empty item list |

## Week 2 — Core domain and first deploy (mid-term)

| Ticket | Feature | Done when |
|---|---|---|
| INV-9 | Stock movements (in / out / adjust with reason), atomic update, history | Oversized stock-out returns 409; concurrency test passes |
| INV-10 | Low-stock crossing detection and outbox | Crossing fires; staying low doesn't; created-low fires once |
| INV-11 | Item list API: search, category and low-stock filters, sort, pagination, indexes | Integration tests pass |
| INV-12 | React items: list states, validated create/edit forms, adjust dialog | Component test: invalid submit blocked |
| INV-13 | CloudFormation: VPC (public/private, 2 AZs), security groups, tagging | Stack deployed, tagged `project=inventory` |
| INV-14 | RDS Postgres (private), credentials in Secrets Manager, KMS | App connects from ECS |
| INV-15 | ECR, ECS Fargate, ALB, task and execution roles, CloudWatch logs, injected secrets | API reachable via ALB |
| INV-16 | GitHub OIDC role; deploy on merge to `main`; `sandbox` environment with approval gate | Merge → deploy after approval |

## Week 3 — Async, alerts, import, UI

| Ticket | Feature | Done when |
|---|---|---|
| INV-17 | SNS topic, SQS alert-queue + DLQ, outbox publisher, email subscription | Test alert email arrives |
| INV-18 | Lambda alert-recorder → DynamoDB (idempotent) | Duplicate message creates no second record |
| INV-19 | API Gateway + JWT authorizer; Lambda alert-reader `GET /alerts` | Returns alerts; no token → 401 |
| INV-20 | React Alerts page | States + component test |
| INV-21 | CSV import backend: presigned upload, jobs, import-queue, worker, validation, upsert, restore | Appendix A → 3 imported, 2 errors with row numbers |
| INV-22 | React import page with row-level error table | Component test |
| INV-23 | Dashboard API and UI | Tests |
| INV-24 | S3 + CloudFront for React (OAC), `/api/*` → ALB, frontend deploy in pipeline | App served from CloudFront |
| INV-25 | Refactor one module (e.g. shared stock-movement domain service) | `refactor:` PR, no behaviour change |

## Week 4 — Operations and handover

| Ticket | Feature | Done when |
|---|---|---|
| INV-26 | CloudWatch dashboard + alarms (DLQ > 0, ALB 5xx, unhealthy targets) | Alarms tested |
| INV-27 | ECS autoscaling + rolling deploy with circuit breaker | Demonstrated |
| INV-28 | Failed deployment exercise: break, diagnose, fix | Written up in lessons learned |
| INV-29 | IAM review, CloudTrail review, secrets scan | Checklist done |
| INV-30 | Performance and accessibility: 10k-item seed, k6 p95, Lighthouse | Results recorded against A9 |
| INV-31 | README, architecture diagram, design note, lessons learned | Committed |
| INV-32 | Demo rehearsal + clean-up (stop RDS, ECS to 0, check budget) | Done |

## Stretch (only if ahead)

- Export the item list to CSV
- Barcode scan field on the adjust dialog
- Custom alert recipients per item
- Weekly low-stock summary on a schedule
