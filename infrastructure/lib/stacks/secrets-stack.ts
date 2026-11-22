import * as cdk from 'aws-cdk-lib';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as kms from 'aws-cdk-lib/aws-kms';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

/**
 * Value of the agent signing private key secret and the agent public key parameter until the owner
 * runs scripts/deploy/create-agent-signing-key.sh. The shared JWT decoder reads `none` as "no key"
 * (agent tokens off), so a deploy before the key exists starts every service safely.
 */
export const AGENT_KEY_PLACEHOLDER = 'none';

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
  public readonly watchJwtSecret: secretsmanager.Secret;
  public readonly secretsKey: kms.Key;
  /** RS256 private key (PKCS#8 PEM) that CUMS signs agent tokens with. CUMS only. */
  public readonly agentSigningPrivateKey: secretsmanager.Secret;
  /** RSA public key (SPKI PEM) that every service verifies agent tokens with. */
  public readonly agentJwtPublicKey: ssm.StringParameter;
  /** Secret EMS presents to CUMS when it asks for an agent token. CUMS and EMS. */
  public readonly emsCallerSecret: secretsmanager.Secret;
  /** Secret the agent service presents to CUMS. CUMS only until the agent runtime exists. */
  public readonly agentServiceCallerSecret: secretsmanager.Secret;
  /** Anthropic API key of the agent service's model adapter (AD-14). agent-service only. */
  public readonly anthropicApiKey: secretsmanager.Secret;

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

    // Watch JWT signing secret for Apple Watch organizer authentication.
    // Intentionally uses the default AWS-managed aws/secretsmanager key (no encryptionKey).
    // Using the customer-managed secretsKey causes a CDK cyclic cross-stack dependency:
    // secret.grantRead() wraps the grantee in a ViaServicePrincipal which cannot receive
    // an IAM policy, so CDK falls back to modifying the KMS key policy in SecretsStack
    // with the ECS execution role ARN from CompanyManagementStack — creating a back-reference.
    // The AWS-managed key is sufficient here; HMAC-SHA256 strength comes from the secret value.
    this.watchJwtSecret = new secretsmanager.Secret(this, 'WatchJWTSecret', {
      secretName: `batbern/${props.config.envName}/watch/jwt-secret`,
      description: 'Watch JWT signing secret for Apple Watch organizer authentication',
      generateSecretString: {
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 64,
        excludePunctuation: true,
      },
    });

    // Agent identity (agentic interaction 0.3). All four use the AWS-managed key, for the same
    // reason as the Watch secret above (cyclic cross-stack KMS policy).
    //
    // The key pair: CDK creates both with the placeholder `none` so the resources exist before
    // the first deploy (ECS fails a task whose secret does not exist) and the decoder starts with
    // agent tokens off. scripts/deploy/create-agent-signing-key.sh writes the real pair once.
    // DO NOT change the properties of these two resources (description, name, value, and the
    // tags this stack and config.tags put on them): CloudFormation would write `none` back on the
    // next deploy, which turns agent tokens off. If that happened, re-run the script
    // with --force and force a new deployment of the four services.
    this.agentSigningPrivateKey = new secretsmanager.Secret(this, 'AgentSigningPrivateKey', {
      secretName: `batbern/${props.config.envName}/agent/signing-private-key`,
      description:
        'RS256 private key (PKCS#8 PEM) CUMS signs agent tokens with; written by scripts/deploy/create-agent-signing-key.sh',
      secretStringValue: cdk.SecretValue.unsafePlainText(AGENT_KEY_PLACEHOLDER),
    });
    this.agentJwtPublicKey = new ssm.StringParameter(this, 'AgentJwtPublicKey', {
      parameterName: `/batbern/${props.config.envName}/agent/jwt-public-key`,
      description:
        'RSA public key (SPKI PEM) that verifies agent tokens; written by scripts/deploy/create-agent-signing-key.sh',
      stringValue: AGENT_KEY_PLACEHOLDER,
    });

    // Caller secrets: generated by CDK, read by CUMS (and EMS for its own) to authenticate the
    // callers that may request agent tokens.
    this.emsCallerSecret = new secretsmanager.Secret(this, 'AgentEmsCallerSecret', {
      secretName: `batbern/${props.config.envName}/agent/ems-caller-secret`,
      description: 'Secret EMS presents to CUMS when it requests an agent token',
      generateSecretString: {
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 64,
        excludePunctuation: true,
      },
    });
    this.agentServiceCallerSecret = new secretsmanager.Secret(this, 'AgentServiceCallerSecret', {
      secretName: `batbern/${props.config.envName}/agent/agent-service-caller-secret`,
      description: 'Secret the agent service presents to CUMS when it requests an agent token',
      generateSecretString: {
        excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'/@"\\',
        passwordLength: 64,
        excludePunctuation: true,
      },
    });

    // The Anthropic API key of agent-service's model adapter (agent runtime, story 3, AD-14).
    // AWS-managed key, as the agent secrets above. CDK creates it with the placeholder `none`: the
    // secret exists before the first deploy, and agent-service reads `none` as "no key" (adapter
    // off, runs handed to the organizers) and still starts. The owner writes the real key with
    // scripts/deploy/put-anthropic-api-key.sh. DO NOT change the properties of this resource
    // (description, name, value, tags): CloudFormation would write `none` back on the next deploy
    // and switch the adapter off. If that happened, re-run the script with --force.
    this.anthropicApiKey = new secretsmanager.Secret(this, 'AgentAnthropicApiKey', {
      secretName: `batbern/${props.config.envName}/agent/anthropic-api-key`,
      description:
        'Anthropic API key of the agent service model adapter; written by scripts/deploy/put-anthropic-api-key.sh',
      secretStringValue: cdk.SecretValue.unsafePlainText(AGENT_KEY_PLACEHOLDER),
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
