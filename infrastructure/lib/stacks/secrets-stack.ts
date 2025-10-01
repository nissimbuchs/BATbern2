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
 */
export class SecretsStack extends cdk.Stack {
  public readonly dbSecret: secretsmanager.Secret;
  public readonly redisSecret: secretsmanager.Secret;
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

    // Database credentials secret
    this.dbSecret = new secretsmanager.Secret(this, 'DatabaseSecret', {
      secretName: `batbern/${props.config.envName}/database/credentials`,
      description: 'RDS PostgreSQL master credentials',
      encryptionKey: this.secretsKey,
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'batbern_admin' }),
        generateStringKey: 'password',
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 32,
        requireEachIncludedType: true,
      },
    });

    // Add rotation schedule for database secret in production
    // Note: Rotation requires RDS integration which will be configured separately
    if (isProd) {
      this.dbSecret.addRotationSchedule('DatabaseSecretRotation', {
        automaticallyAfter: cdk.Duration.days(30),
        hostedRotation: secretsmanager.HostedRotation.postgreSqlSingleUser(),
      });
    }

    // Redis authentication token secret
    this.redisSecret = new secretsmanager.Secret(this, 'RedisSecret', {
      secretName: `batbern/${props.config.envName}/redis/auth-token`,
      description: 'ElastiCache Redis authentication token',
      encryptionKey: this.secretsKey,
      generateSecretString: {
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 32,
        excludePunctuation: true,
      },
    });

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
    new cdk.CfnOutput(this, 'DatabaseSecretArn', {
      value: this.dbSecret.secretArn,
      description: 'ARN of database credentials secret',
      exportName: `${props.config.envName}-DatabaseSecretArn`,
    });

    new cdk.CfnOutput(this, 'RedisSecretArn', {
      value: this.redisSecret.secretArn,
      description: 'ARN of Redis auth token secret',
      exportName: `${props.config.envName}-RedisSecretArn`,
    });

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
