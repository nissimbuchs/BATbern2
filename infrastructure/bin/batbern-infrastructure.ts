#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/stacks/network-stack';
import { DatabaseStack } from '../lib/stacks/database-stack';
import { StorageStack } from '../lib/stacks/storage-stack';
import { SecretsStack } from '../lib/stacks/secrets-stack';
import { MonitoringStack } from '../lib/stacks/monitoring-stack';
import { CICDStack } from '../lib/stacks/cicd-stack';
import { CognitoStack } from '../lib/stacks/cognito-stack';
import { ApiGatewayStack } from '../lib/stacks/api-gateway-stack';
import { FrontendStack } from '../lib/stacks/frontend-stack';
import { DnsStack } from '../lib/stacks/dns-stack';
import { devConfig } from '../lib/config/dev-config';
import { stagingConfig } from '../lib/config/staging-config';
import { prodConfig } from '../lib/config/prod-config';
import { EnvironmentConfig, EnvironmentHelper } from '../lib/config/environment-config';

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

// Validate deployment account matches expected configuration
if (env.account !== config.account) {
  throw new Error(
    `\n❌ ACCOUNT MISMATCH DETECTED!\n` +
    `\n  Current AWS credentials point to account: ${env.account}` +
    `\n  Expected account for ${config.envName}:     ${config.account}` +
    `\n` +
    `\n  This prevents accidental deployment to the wrong AWS account.` +
    `\n` +
    `\n  To fix:` +
    `\n  - Use the npm scripts: npm run deploy:${config.envName}` +
    `\n  - Or set correct profile: export AWS_PROFILE=batbern-${config.envName}` +
    `\n`
  );
}

// Create stacks for the selected environment
const stackPrefix = `BATbern-${config.envName}`;

// 1. DNS Stack (Route53, ACM Certificates) - CENTRALIZED IN MANAGEMENT ACCOUNT
// DNS is managed centrally in the management account (510187933511)
// - Hosted Zone: Z04921951F6B818JF0POD (batbern.ch)
// - Certificates created manually in us-east-1 for CloudFront
// - Each environment references the centralized hosted zone via hostedZoneId in config
let dnsStack: DnsStack | undefined;
// DNS stack creation disabled - using centralized DNS in management account
// if (config.domain && EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
//   const dnsEnv = {
//     account: env.account,
//     region: 'us-east-1', // CloudFront requires certificates in us-east-1
//   };
//
//   dnsStack = new DnsStack(app, `${stackPrefix}-DNS`, {
//     config,
//     domainName: config.domain.frontendDomain.split('.').slice(-2).join('.'), // Extract root domain (batbern.ch)
//     env: dnsEnv,
//     description: `BATbern DNS Infrastructure - ${config.envName}`,
//     tags: config.tags,
//     crossRegionReferences: true, // Allow exporting certificate to other regions
//   });
// }

// 2. Network Stack (VPC, Security Groups)
const networkStack = new NetworkStack(app, `${stackPrefix}-Network`, {
  config,
  env,
  description: `BATbern Network Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// 3. Secrets Stack (Secrets Manager, KMS)
const secretsStack = new SecretsStack(app, `${stackPrefix}-Secrets`, {
  config,
  env,
  description: `BATbern Secrets Management - ${config.envName}`,
  tags: config.tags,
});

// 4. Database Stack (RDS, ElastiCache)
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

// 5. Storage Stack (S3, CloudFront)
const storageStack = new StorageStack(app, `${stackPrefix}-Storage`, {
  config,
  env,
  description: `BATbern Storage Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// 6. Monitoring Stack (CloudWatch, Alarms, Logs)
const monitoringStack = new MonitoringStack(app, `${stackPrefix}-Monitoring`, {
  config,
  env,
  description: `BATbern Monitoring & Observability - ${config.envName}`,
  tags: config.tags,
});

// 7. CI/CD Stack (ECR, IAM roles for GitHub Actions)
const githubRepository = app.node.tryGetContext('githubRepository') || process.env.GITHUB_REPOSITORY || 'nissimbuchs/BATbern2';
const cicdStack = new CICDStack(app, `${stackPrefix}-CICD`, {
  config,
  githubRepository,
  env,
  description: `BATbern CI/CD Infrastructure - ${config.envName}`,
  tags: config.tags,
});

// 8. Cognito Stack (User authentication)
const cognitoStack = new CognitoStack(app, `${stackPrefix}-Cognito`, {
  config,
  env,
  description: `BATbern User Authentication - ${config.envName}`,
  tags: config.tags,
});

// 9. API Gateway Stack (Unified API with routing)
// NOTE: Only deploy for cloud environments (staging/production)
// Development runs API Gateway locally in Docker
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  const apiGatewayStack = new ApiGatewayStack(app, `${stackPrefix}-ApiGateway`, {
    config,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    domainName: config.domain?.apiDomain,
    hostedZoneId: dnsStack?.hostedZone.hostedZoneId || config.domain?.hostedZoneId,
    certificateArn: networkStack.apiCertificate?.certificateArn || config.domain?.apiCertificateArn,
    env,
    description: `BATbern API Gateway - ${config.envName}`,
    tags: config.tags,
  });
  apiGatewayStack.addDependency(cognitoStack);
  apiGatewayStack.addDependency(networkStack); // Depends on Network for certificate
}

// 10. Frontend Stack (React web application)
// NOTE: Only deploy for cloud environments (staging/production)
// Development runs Frontend locally in Docker
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  const frontendStack = new FrontendStack(app, `${stackPrefix}-Frontend`, {
    config,
    logsBucket: storageStack.logsBucket,
    domainName: config.domain?.frontendDomain,
    hostedZoneId: dnsStack?.hostedZone.hostedZoneId || config.domain?.hostedZoneId,
    certificateArn: dnsStack?.certificate.certificateArn || config.domain?.frontendCertificateArn,
    env,
    description: `BATbern Frontend Application - ${config.envName}`,
    tags: config.tags,
    crossRegionReferences: true, // Required to reference us-east-1 certificate from eu-central-1 stack
  });
  frontendStack.addDependency(storageStack);
  if (dnsStack) {
    frontendStack.addDependency(dnsStack);
  }
}

// Add stack tags
cdk.Tags.of(app).add('Project', 'BATbern');
cdk.Tags.of(app).add('ManagedBy', 'CDK');
cdk.Tags.of(app).add('Environment', config.envName);

app.synth();
