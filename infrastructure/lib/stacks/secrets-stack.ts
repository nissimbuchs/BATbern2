import * as cdk from 'aws-cdk-lib';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as kms from 'aws-cdk-lib/aws-kms';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface SecretsStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * Secrets Stack - Provides secure credential storage and rotation
 *
 * Implements:
 * - AC17: Secrets Manager with secure credential storage and rotation
 * - AC4: Security Boundaries with KMS encryption
 *
 * Note: Database credentials are managed by RDS stack automatically
 */
export class SecretsStack extends cdk.Stack {
  // Redis secret removed - Redis disabled for cost optimization
  public readonly jwtSecret: secretsmanager.Secret;
  public readonly secretsKey: kms.Key;

  constructor(scope: Construct, id: string, props: SecretsStackProps) {
    super(scope, id, props);

    const isProd = props.config.envName === 'production';

    // Create KMS key for encrypting secrets
    this.secretsKey = new kms.Key(this, 'SecretsKey', {
      description: `KMS key for BATbern secrets - ${props.config.envName}`,
      enableKeyRotation: true,
      removalPolicy: isProd ? cdk.RemovalPolicy.RETAIN : cdk.RemovalPolicy.DESTROY,
    });

    // Note: Database credentials are automatically created and managed by RDS stack
    // Note: Redis disabled for cost optimization - secret creation removed

    // JWT signing secret for API authentication
    this.jwtSecret = new secretsmanager.Secret(this, 'JWTSecret', {
      secretName: `batbern/${props.config.envName}/jwt/signing-key`,
      description: 'JWT token signing secret',
      encryptionKey: this.secretsKey,
      generateSecretString: {
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 64,
        excludePunctuation: true,
      },
    });

    // SSM Parameter Store for non-sensitive configuration
    new ssm.StringParameter(this, 'EnvironmentParameter', {
      parameterName: `/batbern/${props.config.envName}/config/environment`,
      description: 'Environment name',
      stringValue: props.config.envName,
    });

    new ssm.StringParameter(this, 'RegionParameter', {
      parameterName: `/batbern/${props.config.envName}/config/region`,
      description: 'AWS region',
      stringValue: props.config.region,
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', props.config.envName);
    cdk.Tags.of(this).add('Component', 'Security');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    // Note: Database secret ARN is exported by Database stack
    // Note: Redis secret output removed (Redis disabled for cost optimization)

    new cdk.CfnOutput(this, 'JWTSecretArn', {
      value: this.jwtSecret.secretArn,
      description: 'ARN of JWT signing key secret',
      exportName: `${props.config.envName}-JWTSecretArn`,
    });

    new cdk.CfnOutput(this, 'SecretsKeyId', {
      value: this.secretsKey.keyId,
      description: 'KMS key ID for secrets encryption',
      exportName: `${props.config.envName}-SecretsKeyId`,
    });
  }
}
