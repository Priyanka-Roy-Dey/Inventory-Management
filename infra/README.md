# infra

AWS infrastructure for InventoryApp: CloudFormation templates and Lambda functions. Every resource is tagged `project=inventory`. Started in INV-13.

## Stacks

Deploy in this order from CloudShell (region `eu-north-1`), each with `--tags project=inventory`:

| Stack | Template | Notes |
|---|---|---|
| `inventory-network` | `network.yaml` | VPC, subnets, NAT gateway, security groups (INV-13) |
| `inventory-database` | `database.yaml` | RDS Postgres, Secrets Manager, KMS (INV-14) |
| `inventory-registry` | `registry.yaml` | ECR repository for the API image (INV-15) |
| `inventory-storage` | `storage.yaml` | Images bucket; kept when the service is deleted (INV-15) |
| `inventory-service` | `service.yaml` | ALB and ECS service; needs `--capabilities CAPABILITY_NAMED_IAM`, `ImageTag`, `JwtKeySecretArn` and `AdminSecretArn` (secrets `inventory/jwt-key` and `inventory/first-admin`, created once by hand) (INV-15) |
| `inventory-frontend` | `frontend.yaml` | S3 bucket for the React build, CloudFront with HTTPS; `/api/*` goes to the ALB. Afterwards, update `inventory-storage` with `--parameter-overrides AllowedOrigin=<WebUrl output>` (INV-24) |
| `inventory-monitoring` | `monitoring.yaml` | CloudWatch alarms (API and load balancer 5xx, unhealthy tasks, API CPU and memory, database CPU and storage) emailed through SNS, and the `inventory` dashboard; needs `--parameter-overrides AlarmEmail=<address>`, then the SNS confirmation link clicked (INV-26) |
| `inventory-github-deploy` | `github-deploy.yaml` | GitHub deploy role, trusted through the account's existing GitHub OIDC provider; needs `--capabilities CAPABILITY_NAMED_IAM` and the frontend stack (INV-16, INV-24) |

After the first deploy, `.github/workflows/deploy.yml` rolls out new images: when CI passes on `main` it waits for approval in the `sandbox` environment, builds and pushes the image, stops on HIGH or CRITICAL scan findings, updates only the `ImageTag` of `inventory-service` and checks `/actuator/health`. It then uploads the web build to the frontend bucket, refreshes `index.html` in CloudFront and checks that the app loads and `/api/*` reaches the API. Changes to the templates themselves are deployed by hand.

When you update a stack, `aws cloudformation deploy` keeps each parameter's previous value. If a template's parameter default changes, pass the new value with `--parameter-overrides`, or the stack keeps the old one.

To check that an alarm reaches the inbox without breaking anything, force it into ALARM; it goes back to OK at its next evaluation:

```bash
aws cloudwatch set-alarm-state --alarm-name inventory-api-5xx --state-value ALARM --state-reason "Testing the alarm email"
```

There is no dead-letter-queue alarm yet, because there is no queue until INV-17.
