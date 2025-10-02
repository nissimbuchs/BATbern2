#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/stacks/network-stack';
import { DatabaseStack } from '../lib/stacks/database-stack';
import { StorageStack } from '../lib/stacks/storage-stack';
import { SecretsStack } from '../lib/stacks/secrets-stack';
import { MonitoringStack } from '../lib/stacks/monitoring-stack';
import { CICDStack } from '../lib/stacks/cicd-stack';
import { devConfig } from '../lib/config/dev-config';
import { stagingConfig } from '../lib/config/staging-config';
import { prodConfig } from '../lib/config/prod-config';
import { EnvironmentConfig } from '../lib/config/environment-config';

const app = new cdk.App();

// Get environment from context or environment variable
const environmentName = app.node.tryGetContext('environment') || process.env.ENVIRONMENT || 'development';

// Select configuration based on environment
const configMap: { [key: string]: EnvironmentConfig } = {
  development: devConfig,
  staging: stagingConfig,
  production: prodConfig,
};

const config = configMap[environmentName];
if (!config) {
  throw new Error(`Invalid environment: ${environmentName}. Must be one of: development, staging, production`);
}

// Define environment
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT || config.account,
  region: config.region,
};

console.log(`Deploying BATbern infrastructure for environment: ${config.envName}`);
console.log(`AWS Account: ${env.account}, Region: ${env.region}`);

// Create stacks for the selected environment
const stackPrefix = `BATbern-${config.envName}`;

// 1. Network Stack (VPC, Security Groups)
const networkStack = new NetworkStack(app, `${stackPrefix}-Network`, {
  config,
  env,
  description: `BATbern Network Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// 2. Secrets Stack (Secrets Manager, KMS)
const secretsStack = new SecretsStack(app, `${stackPrefix}-Secrets`, {
  config,
  env,
  description: `BATbern Secrets Management - ${config.envName}`,
  tags: config.tags,
});

// 3. Database Stack (RDS, ElastiCache)
const databaseStack = new DatabaseStack(app, `${stackPrefix}-Database`, {
  config,
  vpc: networkStack.vpc,
  databaseSecurityGroup: networkStack.databaseSecurityGroup,
  cacheSecurityGroup: networkStack.cacheSecurityGroup,
  env,
  description: `BATbern Database Infrastructure - ${config.envName}`,
  tags: config.tags,
});
databaseStack.addDependency(networkStack);
databaseStack.addDependency(secretsStack);

// 4. Storage Stack (S3, CloudFront)
const storageStack = new StorageStack(app, `${stackPrefix}-Storage`, {
  config,
  env,
  description: `BATbern Storage Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// 5. Monitoring Stack (CloudWatch, Alarms, Logs)
const monitoringStack = new MonitoringStack(app, `${stackPrefix}-Monitoring`, {
  config,
  env,
  description: `BATbern Monitoring & Observability - ${config.envName}`,
  tags: config.tags,
});

// 6. CI/CD Stack (ECR, IAM roles for GitHub Actions)
const githubRepository = app.node.tryGetContext('githubRepository') || process.env.GITHUB_REPOSITORY || 'YOUR_ORG/BATbern';
const cicdStack = new CICDStack(app, `${stackPrefix}-CICD`, {
  config,
  githubRepository,
  env,
  description: `BATbern CI/CD Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// Add stack tags
cdk.Tags.of(app).add('Project', 'BATbern');
cdk.Tags.of(app).add('ManagedBy', 'CDK');
cdk.Tags.of(app).add('Environment', config.envName);

app.synth();
