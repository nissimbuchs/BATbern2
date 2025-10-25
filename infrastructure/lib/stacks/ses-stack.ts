import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface SesStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * SES Stack - Provides email templates for authentication workflows
 *
 * Note: Password reset emails are now handled via CustomMessage Lambda trigger
 * (Story 1.2.2) which provides HTML directly to Cognito, not via SES templates.
 *
 * This stack is reserved for future SES templates if needed.
 */
export class SesStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: SesStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Email');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Future SES templates can be added here
  }
}
