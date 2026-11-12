# Demo and clean-up (INV-32)

How to prepare, run and wind down the InventoryApp demo. The demo follows the four journeys in the [architecture diagram](../README.md#architecture): opening the app, using the API, uploading an image, and deploying. It takes about 20 minutes, plus questions.

## 1. Before the demo (30 minutes ahead)

### The account
- [ ] The `Admin` IAM user has MFA, and you are signed in as `Admin`, not root ([security review](security-review.md#actions), action 1).
- [ ] CloudShell is open in `eu-north-1`, with the prompt `~ $`.

### The app is up
If it was scaled down, start it first ([section 4](#4-starting-it-again)). Then, in CloudShell:

```bash
curl -s $(aws cloudformation describe-stacks --stack-name inventory-service --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)/actuator/health; echo
```

- [ ] It prints `{"status":"UP"}`, and https://d1v2v55o96jhmt.cloudfront.net shows the sign-in page.
- [ ] Every CloudWatch alarm is `OK` (CloudWatch → Alarms).

### Demo data
- [ ] Sign in as Admin. Under **Users**, create `demo-staff` (Staff) and `demo-viewer` (Viewer). Keep their one-time passwords for the demo, but not in any file or screenshot.
- [ ] Create an item that is one stock-out away from low stock, for example SKU `DEMO-1`, quantity 12, reorder level 10.
- [ ] Have a small image (JPEG or PNG) ready for a stock-in.
- [ ] Download the sample CSV: https://d1v2v55o96jhmt.cloudfront.net/sample-import.csv (6 rows: 3 import, 2 are refused, and 1 repeats a SKU).
- [ ] Prepare a tiny pull request, for example a wording change in `docs/demo.md`, ready to merge.

### Tabs to have open
1. The app: https://d1v2v55o96jhmt.cloudfront.net
2. GitHub: the repository's README (architecture diagram), and **Actions**
3. CloudWatch → Dashboards → `inventory`
4. Your mailbox, for the alarm email

## 2. Demo script

### Part 1: the app (about 10 minutes)

| Step | What to do | What to point out |
|---|---|---|
| 1 | Open the app and sign in as Admin | HTTPS through CloudFront; one address for the app and the API |
| 2 | **Dashboard** | Stock totals, low-stock count and recent movements, computed by Postgres |
| 3 | **Items**: search, filter by category and low stock, sort, page | Server-side search with indexes. Refresh on `/items` still works (CloudFront serves `index.html`) |
| 4 | Create an item; then try the same SKU again | Validation; a duplicate SKU is refused (`409`) |
| 5 | On `DEMO-1`, **stock in** with the image | The image goes straight from the browser to S3 through a presigned URL, and shows on the Items page |
| 6 | **Stock out** more than the item holds | Refused with a clear message: stock never goes below 0, even when two people change it at the same moment (one atomic update, design note 2.2) |
| 7 | **Stock out** 3 from `DEMO-1` (12 → 9) | It crossed its reorder level of 10, so an alert is recorded in the same transaction (outbox) |
| 8 | **Alerts** | The new alert. Another stock-out while it stays low adds no second alert (design note 2.3) |
| 9 | **Import**: upload the sample CSV | 3 imported, 2 errors with their row numbers; bad rows are skipped, good ones are saved |
| 10 | Sign out; sign in as `demo-viewer` with its one-time password | Forced password change first; then read-only: no create, edit or stock buttons, and the API answers `403` if tried |
| 11 | Sign in as Admin; delete an item | Only an Admin can delete. The item disappears everywhere, but its SKU stays reserved |

### Part 2: how it is built and run (about 10 minutes)

| Step | What to show | What to point out |
|---|---|---|
| 12 | README → **Architecture** | Walk the diagram: CloudFront → load balancer → Fargate → RDS in private subnets; presigned images; Secrets Manager; monitoring |
| 13 | Merge the tiny pull request | The `main` ruleset: it cannot merge until the 3 checks pass on its latest commit |
| 14 | **Actions** → the new Deploy run → **Review deployments** → approve | The approval gate. Credentials come from OIDC and last at most an hour; nothing is stored in GitHub |
| 15 | While it runs: the steps | Image scan gate (stops on any HIGH or CRITICAL), rollout with automatic rollback, web publish, smoke tests through the load balancer and CloudFront. The whole run takes about 10 minutes, so come back to it at the end |
| 16 | CloudWatch dashboard `inventory` | Requests, p95 latency, errors, healthy tasks, CPU and memory, database. The demo's own traffic shows up |
| 17 | Trigger a test alarm (command below) and show the email | Alarms are emailed through SNS, and recover by themselves |
| 18 | Autoscaling (design note A46) | 1 to 3 tasks, on CPU and on requests per task |
| 19 | [Lessons learned](lessons-learned.md) and [security review](security-review.md) | Ten failed deploy attempts, each diagnosed from CloudTrail or stack events; the least-privilege roles; no secrets in the repository |
| 20 | Back to **Actions** | The deploy has finished green |

Test alarm for step 17:

```bash
aws cloudwatch set-alarm-state --alarm-name inventory-api-5xx --state-value OK --state-reason "Reset" && aws cloudwatch set-alarm-state --alarm-name inventory-api-5xx --state-value ALARM --state-reason "Demo of the alarm email"
```

### Questions to be ready for

| Question | Short answer |
|---|---|
| Why Fargate rather than EC2? | No servers to patch or size, and you pay per task; at this scale the saved effort outweighs EC2's lower price at steady high load (design note 2.1) |
| What stops two people over-selling stock? | One conditional `UPDATE ... WHERE quantity + delta >= 0`; Postgres row locks serialise concurrent changes (2.2) |
| How do you avoid losing or duplicating alerts? | The alert is written in the same transaction as the stock change (outbox). The SNS/SQS/Lambda delivery is designed, and deferred (section 4) |
| What isn't built? | Design note, section 4: the asynchronous alert pipeline, the queued import, a refactor and the performance tests, each with a working stand-in |
| How is it secured? | Private subnets, private buckets, secrets in Secrets Manager, OIDC deploys behind approval, least-privilege roles ([security review](security-review.md)) |
| What did you learn? | [Lessons learned](lessons-learned.md): read AWS's own records first, and run a pipeline end to end before relying on it |

## 3. After the demo: scale down

Stops the API and the database without deleting anything. Run in CloudShell, in this order:

```bash
cd ~/Inventory-Management && git switch main && git pull && cd infra
# 1. API to 0 tasks; autoscaling turns off with it (A46).
aws cloudformation deploy --stack-name inventory-service --template-file service.yaml --capabilities CAPABILITY_NAMED_IAM --tags project=inventory --parameter-overrides DesiredCount=0
# 2. Stop the database (after the API, so nothing is using it).
aws rds stop-db-instance --db-instance-identifier inventory-db --query "DBInstance.DBInstanceStatus" --output text
# 3. Check: 0 tasks, and the database stopping.
aws ecs describe-services --cluster inventory --services inventory-api --query "services[0].[desiredCount,runningCount]" --output text && aws rds describe-db-instances --db-instance-identifier inventory-db --query "DBInstances[0].DBInstanceStatus" --output text
```

While it is scaled down:
- **Don't merge to `main`.** The deploy would fail its smoke test, because there is no API to answer.
- **AWS starts a stopped database again after 7 days.** Stop it again if the break is longer.
- The app shows an error, and the load balancer answers `503`.

### What still costs money while scaled down

| Resource | Roughly |
|---|---|
| NAT gateway | about $1.10 a day, the largest item |
| Load balancer | about $0.55 a day |
| RDS storage (20 GB) and the old snapshot | a few cents a day |
| 3 secrets, 7 alarms, the KMS keys | about $3 a month together |
| CloudFront, S3, ECR | cents, with no traffic |

Check the real figures in **Billing → Cost Explorer**. The $1 budget alert is lower than this, so it will keep emailing; set it to the cost you expect, for example $40 a month.

To stop the NAT gateway and load balancer costs too, delete the `inventory-monitoring`, `inventory-frontend` and `inventory-service` stacks, in that order. The database, buckets, secrets and image stay. Recreating them means following [infra/README.md](../infra/README.md) again, so only do this for a long break.

## 4. Starting it again

```bash
cd ~/Inventory-Management && git switch main && git pull && cd infra
# 1. Database first, and wait until it is available (about 5 minutes).
aws rds start-db-instance --db-instance-identifier inventory-db --query "DBInstance.DBInstanceStatus" --output text && aws rds wait db-instance-available --db-instance-identifier inventory-db
# 2. Then the API; autoscaling comes back with it.
aws cloudformation deploy --stack-name inventory-service --template-file service.yaml --capabilities CAPABILITY_NAMED_IAM --tags project=inventory --parameter-overrides DesiredCount=1
# 3. Check.
aws ecs describe-services --cluster inventory --services inventory-api --query "services[0].[desiredCount,runningCount]" --output text
```

The API is ready when the second number is `1` and the health check in [section 1](#the-app-is-up) prints `UP`, usually 2 to 3 minutes after step 2.

## 5. Rehearsal log

| Date | Part | Time taken | What to change |
|---|---|---|---|
| | Part 1 (app) | | |
| | Part 2 (platform) | | |
| | Scale down and start again | | |
