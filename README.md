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

_To be completed in INV-3 / INV-8._

## Running locally

_To be completed in INV-3 / INV-8._

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
