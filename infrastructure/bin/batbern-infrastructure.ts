#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { DnsStack } from '../lib/stacks/dns-stack';
import { NetworkStack } from '../lib/stacks/network-stack';
import { DatabaseStack } from '../lib/stacks/database-stack';
import { StorageStack } from '../lib/stacks/storage-stack';
import { SecretsStack } from '../lib/stacks/secrets-stack';
import { MonitoringStack } from '../lib/stacks/monitoring-stack';
import { CICDStack } from '../lib/stacks/cicd-stack';
import { CognitoStack } from '../lib/stacks/cognito-stack';
import { SesStack } from '../lib/stacks/ses-stack';
import { ApiGatewayStack } from '../lib/stacks/api-gateway-stack';
import { FrontendStack } from '../lib/stacks/frontend-stack';
import { ClusterStack } from '../lib/stacks/cluster-stack';
import { ApiGatewayServiceStack } from '../lib/stacks/api-gateway-service-stack';
import { EventManagementStack } from '../lib/stacks/event-management-stack';
import { SpeakerCoordinationStack } from '../lib/stacks/speaker-coordination-stack';
import { PartnerCoordinationStack } from '../lib/stacks/partner-coordination-stack';
import { AttendeeExperienceStack } from '../lib/stacks/attendee-experience-stack';
import { CompanyManagementStack } from '../lib/stacks/company-management-stack';
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

// 1. DNS & Certificates - Subdomain Delegation Architecture
// - Production account owns batbern.ch hosted zone
// - Staging account owns staging.batbern.ch delegated subdomain
// - Each environment creates certificates with automatic DNS validation
// - Frontend certificates in us-east-1 (for CloudFront)
// - API certificates in eu-central-1 (for API Gateway)
let dnsStack: DnsStack | undefined;
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  const domainName = config.envName === 'production' ? 'batbern.ch' : `${config.envName}.batbern.ch`;
  dnsStack = new DnsStack(app, `${stackPrefix}-DNS`, {
    config,
    domainName,
    env: {
      account: env.account,
      region: 'us-east-1', // CloudFront requires certificates in us-east-1
    },
    description: `BATbern DNS & Certificates - ${config.envName}`,
    tags: config.tags,
    crossRegionReferences: true, // Required to reference us-east-1 certificate from eu-central-1
  });
}

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

// 9. SES Stack (Email templates for authentication workflows)
const sesStack = new SesStack(app, `${stackPrefix}-SES`, {
  config,
  env,
  description: `BATbern Email Templates - ${config.envName}`,
  tags: config.tags,
});

// 10. ECS Cluster Stack (Shared cluster for all microservices)
// NOTE: Only deploy for cloud environments (staging/production)
// Development runs microservices locally in Docker
let clusterStack: ClusterStack | undefined;
let eventManagementStack: EventManagementStack | undefined;
let speakerCoordinationStack: SpeakerCoordinationStack | undefined;
let partnerCoordinationStack: PartnerCoordinationStack | undefined;
let attendeeExperienceStack: AttendeeExperienceStack | undefined;
let companyManagementStack: CompanyManagementStack | undefined;
let apiGatewayServiceStack: ApiGatewayServiceStack | undefined;

if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  // Create shared ECS cluster
  clusterStack = new ClusterStack(app, `${stackPrefix}-Cluster`, {
    config,
    vpc: networkStack.vpc,
    env,
    description: `BATbern ECS Cluster - ${config.envName}`,
    tags: config.tags,
  });
  clusterStack.addDependency(networkStack);

  // 10a. Event Management Service
  eventManagementStack = new EventManagementStack(app, `${stackPrefix}-EventManagement`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    env,
    description: `BATbern Event Management Service - ${config.envName}`,
    tags: config.tags,
  });
  eventManagementStack.addDependency(clusterStack);
  eventManagementStack.addDependency(databaseStack);
  eventManagementStack.addDependency(cicdStack);
  eventManagementStack.addDependency(cognitoStack);

  // 10b. Speaker Coordination Service
  speakerCoordinationStack = new SpeakerCoordinationStack(app, `${stackPrefix}-SpeakerCoordination`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    env,
    description: `BATbern Speaker Coordination Service - ${config.envName}`,
    tags: config.tags,
  });
  speakerCoordinationStack.addDependency(clusterStack);
  speakerCoordinationStack.addDependency(databaseStack);
  speakerCoordinationStack.addDependency(cicdStack);
  speakerCoordinationStack.addDependency(cognitoStack);

  // 10c. Partner Coordination Service
  partnerCoordinationStack = new PartnerCoordinationStack(app, `${stackPrefix}-PartnerCoordination`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    env,
    description: `BATbern Partner Coordination Service - ${config.envName}`,
    tags: config.tags,
  });
  partnerCoordinationStack.addDependency(clusterStack);
  partnerCoordinationStack.addDependency(databaseStack);
  partnerCoordinationStack.addDependency(cicdStack);
  partnerCoordinationStack.addDependency(cognitoStack);

  // 10d. Attendee Experience Service
  attendeeExperienceStack = new AttendeeExperienceStack(app, `${stackPrefix}-AttendeeExperience`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    env,
    description: `BATbern Attendee Experience Service - ${config.envName}`,
    tags: config.tags,
  });
  attendeeExperienceStack.addDependency(clusterStack);
  attendeeExperienceStack.addDependency(databaseStack);
  attendeeExperienceStack.addDependency(cicdStack);
  attendeeExperienceStack.addDependency(cognitoStack);

  // 10e. Company Management Service
  companyManagementStack = new CompanyManagementStack(app, `${stackPrefix}-CompanyManagement`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    env,
    description: `BATbern Company Management Service - ${config.envName}`,
    tags: config.tags,
  });
  companyManagementStack.addDependency(clusterStack);
  companyManagementStack.addDependency(databaseStack);
  companyManagementStack.addDependency(cicdStack);
  companyManagementStack.addDependency(cognitoStack);

  // 10f. API Gateway Service (Spring Boot)
  apiGatewayServiceStack = new ApiGatewayServiceStack(app, `${stackPrefix}-ApiGatewayService`, {
    config,
    cluster: clusterStack.cluster,
    vpc: networkStack.vpc,
    databaseEndpoint: databaseStack.databaseEndpoint,
    cacheEndpoint: databaseStack.cacheEndpoint,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    eventManagementServiceUrl: eventManagementStack.serviceUrl,
    speakerCoordinationServiceUrl: speakerCoordinationStack.serviceUrl,
    partnerCoordinationServiceUrl: partnerCoordinationStack.serviceUrl,
    attendeeExperienceServiceUrl: attendeeExperienceStack.serviceUrl,
    companyManagementServiceUrl: companyManagementStack.serviceUrl,
    env,
    description: `BATbern API Gateway Service (Spring Boot) - ${config.envName}`,
    tags: config.tags,
  });
  apiGatewayServiceStack.addDependency(clusterStack);
  apiGatewayServiceStack.addDependency(databaseStack);
  apiGatewayServiceStack.addDependency(cicdStack);
  apiGatewayServiceStack.addDependency(cognitoStack);
  apiGatewayServiceStack.addDependency(eventManagementStack);
  apiGatewayServiceStack.addDependency(speakerCoordinationStack);
  apiGatewayServiceStack.addDependency(partnerCoordinationStack);
  apiGatewayServiceStack.addDependency(attendeeExperienceStack);
  apiGatewayServiceStack.addDependency(companyManagementStack);
}

// 11. API Gateway Stack (AWS API Gateway proxy to Spring Boot API Gateway)
// NOTE: Only deploy for cloud environments (staging/production)
// Development runs API Gateway locally in Docker
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  const apiGatewayStack = new ApiGatewayStack(app, `${stackPrefix}-ApiGateway`, {
    config,
    userPool: cognitoStack.userPool,
    userPoolClient: cognitoStack.userPoolClient,
    domainName: config.domain?.apiDomain,
    hostedZoneId: config.domain?.hostedZoneId,
    certificateArn: networkStack.apiCertificate?.certificateArn || config.domain?.apiCertificateArn,
    apiGatewayServiceUrl: apiGatewayServiceStack?.apiGatewayUrl, // Spring Boot API Gateway internal ALB
    env,
    description: `BATbern API Gateway - ${config.envName}`,
    tags: config.tags,
  });
  apiGatewayStack.addDependency(cognitoStack);
  apiGatewayStack.addDependency(networkStack); // Depends on Network for certificate
  if (apiGatewayServiceStack) {
    apiGatewayStack.addDependency(apiGatewayServiceStack); // Depends on API Gateway Service for routing
  }
}

// 12. Frontend Stack (React web application)
// NOTE: Only deploy for cloud environments (staging/production)
// Development runs Frontend locally in Docker
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  const frontendStack = new FrontendStack(app, `${stackPrefix}-Frontend`, {
    config,
    logsBucket: storageStack.logsBucket,
    domainName: config.domain?.frontendDomain,
    hostedZoneId: config.domain?.hostedZoneId,
    certificateArn: dnsStack?.certificate.certificateArn,
    env,
    description: `BATbern Frontend Application - ${config.envName}`,
    tags: config.tags,
    crossRegionReferences: true, // Required to reference us-east-1 certificate from eu-central-1 stack
  });
  frontendStack.addDependency(storageStack);
  if (dnsStack) {
    frontendStack.addDependency(dnsStack); // Depends on DNS stack for certificate
  }
}

// Add stack tags
cdk.Tags.of(app).add('Project', 'BATbern');
cdk.Tags.of(app).add('ManagedBy', 'CDK');
cdk.Tags.of(app).add('Environment', config.envName);

app.synth();
