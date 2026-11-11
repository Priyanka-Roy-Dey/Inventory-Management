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
| `inventory-github-deploy` | `github-deploy.yaml` | OIDC provider and the GitHub deploy role; needs `--capabilities CAPABILITY_NAMED_IAM` (INV-16) |

After the first deploy, `.github/workflows/deploy.yml` rolls out new images: when CI passes on `main` it waits for approval in the `sandbox` environment, builds and pushes the image, stops on HIGH or CRITICAL scan findings, updates only the `ImageTag` of `inventory-service` and checks `/actuator/health`. Changes to the templates themselves are deployed by hand.
