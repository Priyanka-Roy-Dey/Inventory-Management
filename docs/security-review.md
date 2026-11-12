# Security review (INV-29)

A review of who can do what in the AWS account, what CloudTrail shows, and whether any secret has ever been committed. Done on 12 November 2026, against account `936719391115`, region `eu-north-1`, and all 114 commits of this repository. The evidence comes from read-only AWS CLI commands run in CloudShell, and from the templates in `infra/`.

✅ = passes · ⚠️ = accepted for the sandbox, with the reason · ❌ = needs action (listed under [Actions](#actions))

## 1. IAM

### InventoryApp roles: least privilege

| Check | Result |
|---|---|
| ✅ The API task role (`inventory-api-task`) can only use the images bucket | `s3:GetObject`, `PutObject` and `DeleteObject` on `inventory-images-…/*`, and `s3:ListBucket` on that bucket (so a missing upload is a 404, not a 403). Nothing else. |
| ✅ The execution role (`inventory-api-execution`) can only read the 3 secrets the API needs | `secretsmanager:GetSecretValue` on `inventory/db`, `inventory/first-admin` and `inventory/jwt-key`; `kms:Decrypt` on the database key only. Plus AWS's standard `AmazonECSTaskExecutionRolePolicy`, which pulls the image and writes logs. |
| ✅ Only approved GitHub jobs can use the deploy role (`inventory-github-deploy`) | Trusted only through GitHub's OIDC provider, for audience `sts.amazonaws.com` and subject `repo:Priyanka-Roy-Dey@36127173/Inventory-Management@1439440613:environment:sandbox`. That means this exact repository (by id), in the `sandbox` environment, after a required reviewer approves. No stored keys; credentials last at most 1 hour. |
| ✅ The deploy role can only write what it deploys | Pushes to the `inventory-api` repository; `UpdateStack` on `inventory-service` only; `UpdateService` on `inventory/inventory-api` only; writes to the web bucket; invalidates the one distribution. `iam:PassRole` is limited to the two API roles, and only to ECS tasks. |
| ⚠️ Some deploy-role actions use `Resource: '*'` | Only where AWS gives no alternative: `ecr:GetAuthorizationToken`, the ECS task definition actions (task definitions have no fixed ARN before they exist), and the read-only `elasticloadbalancing:Describe*` and `application-autoscaling:Describe*` that CloudFormation needs ([lessons learned](lessons-learned.md#68-cloudformation-needed-read-permissions-the-role-did-not-have)). None of them can change anything. |

### Account

| Check | Result |
|---|---|
| ✅ Root has MFA | `AccountMFAEnabled = 1`; the credential report shows `mfa_active = true` for root. |
| ✅ Root has no access keys | `AccountAccessKeysPresent = 0`. |
| ✅ No IAM user has access keys | The only IAM user, `Admin`, has none. Nothing outside AWS holds long-lived keys; GitHub uses OIDC. |
| ❌ The `Admin` IAM user has no MFA | `mfa_active = false`, although it was used today. **Action 1.** |
| ⚠️ Root is used to sign in to the console | Root signed in on 26 and 29 October and 10 November; only the last one used MFA (MFA was turned on in between). Daily work should use `Admin` once it has MFA. **Action 1.** |
| ⚠️ No account password policy | `NoSuchEntity`. There is one human user. **Action 4** (optional). |
| ⚠️ No IAM Access Analyzer | It would flag anything shared outside the account. It is free. **Action 5** (optional). |
| ⚠️ Roles not used by InventoryApp | `github-demo-app-ecr` and `ecsTaskExecutionRole` belong to an earlier project and are left untouched. The `AWSServiceRoleFor…` roles are created and managed by AWS services (ECS, RDS, load balancing, autoscaling, CloudFront). |

## 2. CloudTrail

| Check | Result |
|---|---|
| ⚠️ CloudTrail keeps the default 90-day event history only | `describe-trails` lists no trail. The event history is always on and free, and it was enough to diagnose every failed deploy ([lessons learned](lessons-learned.md)). There is no long-term, tamper-evident copy. **Action 3** (optional). |
| ✅ No unexpected console sign-ins | The 7 sign-ins recorded since 26 October all come from the same network as each other. The 3 failures (26 October) sit between successful sign-ins minutes apart, so they look like mistyped passwords. |
| ✅ No root API activity in `eu-north-1` | No events with user name `root` in the region's 90-day history. Everything was done as `Admin` or through roles. |
| ✅ No refused calls recently | None of the last 50 management events has an `errorCode`. The refusals during the deploy fixes earlier today are explained in [lessons learned](lessons-learned.md). |

## 3. Secrets

| Check | Result |
|---|---|
| ✅ No secret in the repository or its history | All 114 commits on every branch were searched for AWS access keys, GitHub, Sonar and Slack tokens, private keys, and `.env`, `.pem`, `.key`, `.p12`, `.jks` or `credentials` files. None found. The only match is `SigningKeyTests.java`, which wraps a key generated during the test in PEM headers. |
| ✅ Development-only values stay out of the app | `api/compose.yaml` holds the throwaway local database's password, and `web/src/mocks/handlers.js` holds fake users for `npm run dev:mock`. The bundle CloudFront serves was checked and does not contain the mock passwords. |
| ✅ The secrets the app uses live in Secrets Manager | `inventory/db` (password generated by AWS, never shown), `inventory/jwt-key` and `inventory/first-admin`. ECS injects them into the task; they are never in a template, an image or a log. |
| ✅ GitHub holds no AWS credentials | The deploy uses OIDC. The only GitHub secret is `SONAR_TOKEN`. The deploy role's ARN is a variable, not a secret. |
| ⚠️ Secrets are not rotated automatically | `RotationEnabled = None` for all three. Acceptable for a sandbox: the database is private, and the JWT key can be replaced by hand (all tokens then expire within 15 minutes). |
| ⚠️ An employer email address appears as the author of 4 early commits | `aab5adf`, `c719337`, `e7273b2` and `a8a14b6` (30 October). It is not a secret, but the repository is public. Every later commit uses the project address. Rewriting `main`'s history would change every commit after them, so it is recorded instead. |

## 4. Storage

| Check | Result |
|---|---|
| ✅ InventoryApp buckets block all public access | `inventory-images-…` and `inventory-web-…`: all four public-access-block settings on. Both refuse plain HTTP. Only CloudFront can read the web bucket (Origin Access Control); images are reached through presigned URLs only. |
| ❌ Another bucket in the account has public access blocking off | `react-form-s3-bucket` (not part of InventoryApp) has all four settings off. **Action 2.** |

## Actions

| # | Action | Priority | Who |
|---|---|---|---|
| 1 | Turn on MFA for the `Admin` IAM user (IAM → Users → Admin → Security credentials → Assign MFA device), then use `Admin` instead of root | **Must**, before the demo | Account owner, in the console |
| 2 | Check whether `react-form-s3-bucket` needs to be public. If not, turn on Block Public Access (S3 → the bucket → Permissions) | **Should** | Account owner |
| 3 | Create a CloudTrail trail with log file validation, for a long-term, tamper-evident record. The first trail of management events is free; only its S3 storage costs | Optional | Later ticket |
| 4 | Set an account password policy (length 14+, symbols) | Optional | Account owner |
| 5 | Create an IAM Access Analyzer for the account (free) | Optional | Account owner |

Actions 1 and 2 change security settings, so they are left to the account owner rather than done by a script.
