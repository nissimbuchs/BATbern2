#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { CognitoStack } from '../lib/cognito-stack';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION || 'eu-central-1',
};

// Create Cognito Stack
new CognitoStack(app, 'BATbernCognitoStack', {
  env,
  description: 'BATbern Platform - Cognito Authentication Stack',
  tags: {
    Project: 'BATbern',
    Environment: process.env.ENVIRONMENT || 'development',
    ManagedBy: 'CDK',
  },
});

app.synth();