# Design note

Key design decisions for InventoryApp and the reasons for them. Where the assignment does not specify something, the assumption made and its reason are recorded in the [assumption log](#assumption-log).

## 1. Architecture overview

- **Front end:** React build served from a private S3 bucket through CloudFront (Origin Access Control). CloudFront routes `/api/*` to the ALB, so the browser talks to a single domain.
- **API:** Spring Boot on ECS Fargate (2+ tasks) behind an Application Load Balancer, image in ECR.
- **Database:** RDS PostgreSQL in private subnets, reachable only from the ECS security group.
- **Network:** VPC across 2 Availability Zones. Public subnets: ALB, NAT gateway. Private subnets: ECS tasks, RDS.
- **Alerts:** stock movement transaction → outbox → SNS topic → email subscription (manager) and SQS alert-queue (+DLQ) → Lambda → DynamoDB. Alerts page reads history through API Gateway → Lambda → DynamoDB.
- **CSV import:** presigned S3 upload → import job → SQS import-queue → worker in the API → row-by-row validation and upsert.
- **Secrets:** Secrets Manager (DB credentials, JWT signing key, initial Admin), injected into ECS tasks. KMS encryption at rest.
- **Monitoring:** CloudWatch logs, dashboard and alarms; CloudTrail for API auditing.

_Architecture diagram: `docs/architecture.png` (INV-31)._

## 2. Key decisions

### 2.1 Fargate vs EC2

_To be completed._ Starting position: Fargate. No servers to patch or scale, and you pay per task. EC2 is cheaper at steady high load but adds instance management, AMI patching and capacity planning. For a small app with variable load and one developer, the lower operational effort outweighs the cost difference.

### 2.2 Concurrent stock adjustments

A stock movement is a single atomic conditional update:

```sql
UPDATE items
SET quantity = quantity + :delta, version = version + 1, updated_at = now()
WHERE id = :id AND deleted_at IS NULL AND quantity + :delta >= 0
RETURNING quantity - :delta AS qty_before, quantity AS qty_after, reorder_level;
```

No row returned means the stock-out exceeds available quantity → `409`. Postgres row locking serialises concurrent updates, so no count is lost. Item edits (name, price, etc.) use optimistic locking (`@Version`). A `CHECK (quantity >= 0)` constraint is the last line of defence.

### 2.3 Low-stock alert rule

An alert fires when `qty_before > reorder_level AND qty_after <= reorder_level`. While the item stays low, `qty_before` is already at or below the threshold, so no further alert fires. Going back above and dropping again is a new crossing.

### 2.4 Item created already at or below its reorder level

Creation (through the UI, API or CSV import) is treated as a crossing from "not low", so the item alerts **once**. Reason: the manager needs to reorder it. With the sample CSV, PAP-A4 and CBL-USB each alert once.

### 2.5 Reliable alert delivery (outbox pattern)

The alert event is written to an `outbox_events` table in the same transaction as the stock movement. A publisher sends unpublished events to SNS. This prevents a lost alert if the app crashes after the database commit. SQS delivery is at-least-once, so the Lambda writes to DynamoDB with a conditional put on `alertId` to stay idempotent. Failed messages go to a DLQ with a CloudWatch alarm.

### 2.6 CSV import

Files go straight to S3 by presigned URL, so large files never pass through the API. The API returns `202 Accepted` with a job ID, and a worker processes the file from the SQS import-queue in batches. Each batch is its own transaction; invalid rows go to the error report and do not fail the file. Rows are upserted by SKU, which makes re-imports idempotent.

### 2.7 Authentication

JWTs issued by the API (Spring Security), signed with RS256. The private key is in Secrets Manager; the public key is published at a JWKS endpoint so API Gateway can verify tokens without sharing a secret. Passwords are hashed with BCrypt.

## 3. Assumption log

| # | Assumption | Reason |
|---|---|---|
| A1 | Admin creates all users; no self-registration. First Admin is seeded from configuration. | Manager default; simplest secure option. |
| A2 | Admin resets a password to a temporary one; the user must change it at next sign-in. | Manager default; no email dependency. |
| A3 | Alert recipient address comes from configuration or Secrets Manager. | Manager default; not hard-coded. |
| A4 | Alert email is sent through an SNS topic with an email subscription, confirmed before the demo. | Manager default; SNS is an approved service. |
| A5 | A repeated SKU in a CSV is an update (last row wins), within a file or across imports. A duplicate SKU on create through the UI/API returns `409`. | Manager default; matches the expected PEN-001 result. |
| A6 | A missing required header rejects the whole file. A row with the wrong field count or an empty/invalid required value goes to the error report. Required columns: `sku, name, category, quantity, reorder_level, unit_price`. | Manager default. |
| A7 | Soft delete, Admin only. The SKU stays reserved (create returns `409`); a CSV row with that SKU restores and updates the item. Deleted items are excluded from the list, dashboard totals and alerts. | Manager default. |
| A8 | Import limit: 10 MB or 50,000 rows; larger files are rejected with a clear message. | Manager default. |
| A9 | WCAG 2.1 AA basics; Lighthouse accessibility ≥ 90 and performance ≥ 80; item list p95 < 500 ms with about 10,000 items. | Manager default. |
| A10 | Own JWTs (Spring Security), own user table, BCrypt. No external identity provider. | Manager default. |
| A11 | Local plus one AWS sandbox environment. The sandbox deploy sits behind a GitHub Environment approval gate to demonstrate the requirement. | Manager default; the gate still shows the CI/CD outcome. |
| A12 | Alert history lives in DynamoDB, served by API Gateway and Lambda. Items, movements, auth, dashboard and CSV import stay in Spring Boot on ECS with RDS. | Manager default. |
| A13 | An item created or imported already at or below its reorder level alerts once. | See 2.4. |
| A14 | Raising the reorder level above the current quantity alerts once (same crossing rule). | Consistent with A13. |
| A15 | Unit price must be ≥ 0; 0 is allowed. | Negative price is tested in the acceptance criteria; 0 covers free samples. |
| A16 | Prices are `NUMERIC(10,2)` in the database and `BigDecimal` in Java. | Exact money arithmetic for total stock value. |
| A17 | Stock changes use one atomic conditional `UPDATE`; item edits use optimistic locking. | See 2.2. |
| A18 | Alerts use the outbox pattern. | See 2.5. |
| A19 | `ecr:GetAuthorizationToken` keeps `Resource: "*"`. | AWS does not support resource-level permissions for this action. |
| A20 | Single-AZ RDS and one NAT gateway in the sandbox. | Cost. Production would use Multi-AZ RDS and a NAT gateway per AZ. |
