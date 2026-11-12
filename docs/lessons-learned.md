# Lessons learned

What went wrong while building and deploying InventoryApp, how each problem was found and fixed, and what changed so it does not happen again. Times are UTC, on 12 November 2026 unless stated otherwise.

## Failed deployment exercise (INV-28)

The exercise did not need a staged break. The first real runs of the GitHub deploy pipeline (INV-16), and the first deploy after INV-26, failed ten times, counting re-runs, for seven different reasons. Each one was diagnosed from the evidence AWS keeps (CloudTrail, CloudFormation stack events, ECS service events), fixed, and guarded against.

| # | Run | Failed at | Root cause | Fix |
|---|---|---|---|---|
| 1 | Deploy #1 | AWS sign-in | Deploy role not created yet | Deployed `inventory-github-deploy` against the account's existing OIDC provider |
| 2 | Deploy #2, attempts 1–2 | AWS sign-in | GitHub sends an id-based OIDC subject | Trust policy matches `repo:owner@ownerId/name@repoId:environment:sandbox` |
| 3 | (caught before a re-run) | Role update | Stack update kept the old parameter value | `--parameter-overrides`; README note |
| 4 | Deploy #2, attempt 3 | AWS sign-in | Role ARN variable ended in `.` | Corrected the `AWS_DEPLOY_ROLE_ARN` variable |
| 5 | Deploy #2, attempt 4 | Scan gate | Scan read 1 second after the push, before it existed | Poll the scan status instead of `aws ecr wait` |
| 6 | Deploy #2, attempt 5 | Rollout | Role could not read the API's IAM roles | `iam:GetRole` on those two roles |
| 7 | Deploy #2, attempt 6 | Rollout (outputs) | Role could not read the load balancer | `elasticloadbalancing:DescribeLoadBalancers` |
| 8 | Deploy #6, attempts 1–3 | Rollout (outputs) | Role could not read target health | `elasticloadbalancing:Describe*`; better failure report |

Deploy #2 succeeded on attempt 7, and Deploy #6 on attempt 4. In every case the API kept serving: a failed sign-in or scan stops before anything changes, and a failed rollout is rolled back by CloudFormation to the image that was running.

### 1. The deploy role did not exist

- **Symptom:** `Could not load credentials from any providers` in the "AWS credentials" step.
- **Diagnosis:** `aws iam get-role --role-name inventory-github-deploy` → `NoSuchEntity`. Creating the stack had failed earlier with `ResourceExistenceCheck`: the template created a GitHub OIDC provider, and the account already had one (an account can have only one per URL).
- **Fix:** the template now trusts the existing provider by ARN instead of creating one (#41), and the stack was deployed.
- **Prevention:** `infra/github-deploy.yaml` says how to create the provider once in a fresh account.

### 2. GitHub's OIDC subject had a different format

- **Symptom:** `Not authorized to perform sts:AssumeRoleWithWebIdentity`.
- **Diagnosis:** CloudTrail records every refused sign-in, with the subject the token carried:
  ```bash
  aws cloudtrail lookup-events --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRoleWithWebIdentity --max-results 5
  ```
  GitHub sent `repo:Priyanka-Roy-Dey@36127173/Inventory-Management@1439440613:environment:sandbox`; the trust policy expected `repo:Priyanka-Roy-Dey/Inventory-Management:environment:sandbox`.
- **Root cause:** the repository uses GitHub's id-based subject format, which adds the owner and repository ids.
- **Fix:** the trust policy matches the id-based subject. It is also stricter: a re-created repository with the same name cannot deploy.
- **Lesson:** match the subject to what CloudTrail shows the token actually carries, not to the format in examples.

### 3. Redeploying the stack changed nothing

- **Symptom:** after the fix, `aws cloudformation deploy` said `No changes to deploy`. Reading the trust policy back caught it before the deploy was re-run.
- **Diagnosis:** `aws iam get-role ... --query "Role.AssumeRolePolicyDocument"` still showed the old subject.
- **Root cause:** when updating a stack, `aws cloudformation deploy` keeps each parameter's previous value. The fix changed the parameter's default, which an existing stack ignores.
- **Fix:** redeployed with `--parameter-overrides GitHubRepository=...`.
- **Prevention:** written down in `infra/README.md`. After every role change, the trust policy is read back before re-running.

### 4. A full stop at the end of the role ARN

- **Symptom:** the same refusal, although the subject now matched exactly.
- **Diagnosis:** the full CloudTrail event listed the role the job had asked for: `arn:aws:iam::936719391115:role/inventory-github-deploy.`, with a trailing `.`.
- **Root cause:** the `AWS_DEPLOY_ROLE_ARN` environment variable was pasted with the full stop from the sentence around it.
- **Fix:** corrected the variable.
- **Lesson:** "not authorized" can also mean "that role does not exist". CloudTrail's `resources` field shows what was really requested.

### 5. The vulnerability scan was read too early

- **Symptom:** `Waiter ImageScanComplete failed ... ScanNotFoundException`, one second after the image was pushed.
- **Root cause:** ECR starts the scan a few seconds after a push, and `aws ecr wait image-scan-complete` gives up on "not found" instead of waiting.
- **Fix:** the step polls the scan status every 10 seconds for up to 5 minutes. It still stops the deploy on `FAILED`, `UNSUPPORTED_IMAGE` or any HIGH or CRITICAL finding.
- **Lesson:** an AWS waiter's failure states are not always what you would expect; check them before relying on one.

### 6–8. CloudFormation needed read permissions the role did not have

- **Symptom:** the rollout step failed with `UPDATE_ROLLBACK_COMPLETE`. Twice the new task had already started and passed its health checks.
- **Diagnosis:** the stack events of the latest update, oldest first:
  ```bash
  aws cloudformation describe-stack-events --stack-name inventory-service --max-items 80 \
    --query "StackEvents[].[Timestamp,LogicalResourceId,ResourceStatus,ResourceStatusReason]" --output text \
    | awk '{print} /UPDATE_IN_PROGRESS.*User Initiated/{exit}' | tac
  ```
  The reasons were, in turn:
  - `not authorized to perform: iam:GetRole`: to fill in `!GetAtt ExecutionRole.Arn` in the new task definition.
  - `elasticloadbalancing:DescribeLoadBalancers`: for the `ApiUrl` output.
  - `elasticloadbalancing:DescribeTargetHealth`: for the target group output added in INV-26.
- **Root cause:** CloudFormation acts with the caller's permissions. Updating one parameter still makes it read every resource that a changed resource or an output refers to. The deploy role was written least-privilege by hand, so each missing read showed up only on the first run that needed it.
- **Fix:** `iam:GetRole` on the two API roles, and the read-only `elasticloadbalancing:Describe*` set (these actions cannot be limited to one resource anyway). The role still cannot create, change or delete anything outside what it deploys.
- **A bug in the diagnosis tooling:** the first "show why it failed" step only printed events whose status contained `FAILED`, so it printed `None`. A failure in an output is recorded on the stack's own `UPDATE_ROLLBACK_IN_PROGRESS` event. The step now includes that event (#46).
- **Prevention:** INV-27 granted the autoscaling reads before its first pipeline run rather than after a failure.

### Re-runs before the fix was deployed

Deploy #2 was re-run once (attempt 2), and Deploy #6 twice (attempts 2 and 3), before the fix had reached AWS: the change was still uncommitted, or the stack had not been redeployed. Each re-run failed exactly as before. The order is now always the same, and each step is checked before the next:

1. Commit and push (on the Mac).
2. Pull and deploy the stack (in CloudShell), then read the change back from AWS.
3. Only then re-run the deploy.

### Deploys undid autoscaling

- **Symptom:** after a load test scaled the API out to 3 tasks (09:27), it was back at 1 task by 09:46, with no scale-in recorded by autoscaling.
- **Diagnosis:** the ECS service events showed `has stopped 2 running tasks` at 09:45:47, and the deploy's new task starting at 09:47. That was Deploy #8, not autoscaling:
  ```bash
  aws ecs describe-services --cluster inventory --services inventory-api --query "services[0].events[].[createdAt,message]" --output text
  aws application-autoscaling describe-scaling-activities --service-namespace ecs --resource-id service/inventory/inventory-api --include-not-scaled-activities
  ```
- **Root cause:** the service's `DesiredCount` was set from a parameter (1), so each stack update set the count back to 1, whatever autoscaling had chosen. Under real load, every deploy would have removed the extra capacity.
- **Fix:** `DesiredCount` is now only set to stop the API (0). Otherwise it is left out, so CloudFormation leaves the count to autoscaling on updates.
- **Autoscaling itself works:** a second load test scaled out to 3 tasks at 11:58, then back in to 2 at 12:17 and to 1 at 12:23, all recorded as `Successful`.

## Other incidents

### The first Admin's secret was deleted

- **What happened:** `aws secretsmanager delete-secret --secret-id inventory/admin` was run while the secret still belonged to the database stack. Afterwards the secret could not be found or restored, so the stack that owned it could not be repaired.
- **Fix:** the database stack was rebuilt (with a final snapshot of the old one). The first Admin now comes from `inventory/first-admin`, created by hand outside every stack, so deleting or recreating a stack cannot take it away.
- **Lesson:** never delete a resource that a stack owns. Change it through the stack. Before any delete, check what will happen to it; advice that it "can be restored for 7–30 days" turned out not to hold here.

### AWS commands run on the wrong machine

- **What happened:** AWS commands meant for CloudShell were twice run on the Mac, whose AWS credentials may belong to another account. Both stopped before reaching AWS (`You must specify a region`).
- **Rule:** the Mac runs `git` only, and CloudShell runs `aws` only. Every CloudShell step starts by checking the prompt reads `~ $`.

### Pull requests merged before their last commit

- **What happened:** #36 was opened from the wrong branch, and #40 merged at `3daa607`, before its final commit. Each needed a follow-up pull request (#37, #38, #41).
- **Fix:** the `main` ruleset (INV-2, design note A45). A pull request can only merge once the three CI checks have passed on its newest commit, on top of the current `main`.

### Vulnerable packages in the runtime image

- **What happened:** the first image, on Ubuntu, had 9 HIGH findings (pcre2, apt). The Alpine image that replaced it still had 1 CRITICAL (zlib).
- **Fix:** the runtime image is `eclipse-temurin:17-jre-alpine` with `apk upgrade` at build time: 0 findings. The pipeline refuses to deploy an image with any HIGH or CRITICAL finding.

## What we would do differently

- **Run the pipeline end to end before merging it.** Most of these failures were found one at a time, by the first run to reach each step.
- **Use CloudTrail and stack events first.** Every one of these failures was explained exactly by AWS's own records; guessing cost more time than reading them.
- **Make failure output complete.** A report that prints `None` is worse than no report, because it looks like there is nothing to see.
- **Grant read-only permissions for whole services where they cannot be scoped anyway,** and keep least privilege for writes.
