#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { CognitoStack } from '../lib/cognito-stack';
import { ApiGatewayStack } from '../lib/api-gateway-stack';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION || 'eu-central-1',
};

const commonTags = {
  Project: 'BATbern',
  Environment: process.env.ENVIRONMENT || 'development',
  ManagedBy: 'CDK',
};

// Create Cognito Stack
const cognitoStack = new CognitoStack(app, 'BATbernCognitoStack', {
  env,
  description: 'BATbern Platform - Cognito Authentication Stack',
  tags: commonTags,
});

// Create API Gateway Stack
const apiGatewayStack = new ApiGatewayStack(app, 'BATbernApiGatewayStack', {
  env,
  description: 'BATbern Platform - API Gateway Stack',
  tags: commonTags,
  userPool: cognitoStack.userPool,
  userPoolClient: cognitoStack.userPoolClient,
  domainName: process.env.API_DOMAIN_NAME,
  hostedZoneId: process.env.HOSTED_ZONE_ID,
  certificateArn: process.env.CERTIFICATE_ARN,
});

// Add dependency
apiGatewayStack.addDependency(cognitoStack);

app.synth();