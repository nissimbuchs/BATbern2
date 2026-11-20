# BATbern Deployment Documentation

**Start here: [CI/CD Pipeline Guide](./cicd-pipeline-guide.md).** It describes the workflows that
run today and how a change reaches production.

Production is a single AWS account (188701360969, profile `batbern-staging`); CloudFormation
stacks keep their `BATbern-staging-*` names. **A PR to `develop` deploys production**, before
merge, on every push to it (Dependabot PRs excluded). The workflows in `.github/workflows/` are
the source of truth.

| Guide | Purpose | Status |
|-------|---------|--------|
| [CI/CD Pipeline Guide](./cicd-pipeline-guide.md) | Workflows, deploy path, rollback, troubleshooting | Current |
| [GitHub Secrets Setup](./github-secrets-setup.md) | Secrets the workflows read | Current |
| [Native Execution](./native-execution.md) | Running the services natively for local development | Current |
| [Hybrid Docker Image Strategy](./hybrid-docker-image-strategy.md) | How CDK bootstraps ECS before images exist | Background |
| [CI/CD Automated Setup](./cicd-automated-setup.md) | 2025 first-time setup (Story 1.4) | Historical |
| [CI/CD Manual Setup](./cicd-setup-guide.md) | 2025 manual setup (Story 1.4) | Historical |
| [Platform Stability Deployment Guide](./platform-stability-deployment-guide.md) | 2026-01 rollout notes | Historical |

Related: [02-infrastructure-deployment.md](../architecture/02-infrastructure-deployment.md),
CDK source in `infrastructure/lib/stacks/`, the beta frontend canary in CLAUDE.md.
