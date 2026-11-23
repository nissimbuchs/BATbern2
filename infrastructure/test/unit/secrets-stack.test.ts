import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { AGENT_KEY_PLACEHOLDER, SecretsStack } from '../../lib/stacks/secrets-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';
import { synthStagingApp } from './helpers/synth-staging-app';

jest.mock('aws-cdk-lib/aws-s3-deployment', () =>
  require('./helpers/synth-staging-app').s3DeploymentWithInlineSite()
);

describe('SecretsStack', () => {
  describe('AC17: Secrets Manager', () => {
    test('should_createSecretsManager_when_secretsStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify secrets are created
      // JWT, Watch JWT, agent signing private key, EMS and agent-service caller secrets, Anthropic
      // API key (agent runtime story 3).
      // DB managed by RDS stack, Redis removed (disabled for cost optimization)
      template.resourceCountIs('AWS::SecretsManager::Secret', 6);
    });

    test('should_enableKMSKeyRotation_when_productionEnvironment', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify KMS key has rotation enabled
      template.hasResourceProperties('AWS::KMS::Key', {
        EnableKeyRotation: true,
      });
    });

    test('should_generateSecurePassword_when_secretCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify secret has generation configuration (not testing specific length - implementation detail)
      template.hasResourceProperties('AWS::SecretsManager::Secret', {
        GenerateSecretString: Match.objectLike({
          ExcludePunctuation: true,
        }),
      });
    });
  });

  describe('AC4: Security Boundaries', () => {
    test('should_encryptSecrets_when_secretsCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify KMS encryption for secrets
      template.resourceCountIs('AWS::KMS::Key', 1);
    });
  });

  describe('Parameter Store Integration', () => {
    test('should_createParameterStoreValues_when_configNeeded', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify SSM parameters are created
      template.resourceCountIs('AWS::SSM::Parameter', 3); // Environment name, region, agent public key
    });
  });

  describe('Agent signing key and caller secrets (agentic interaction 0.3)', () => {
    const template = () =>
      Template.fromStack(
        new SecretsStack(new App(), 'TestSecretsStack', {
          config: devConfig,
          env: { account: '123456789012', region: 'eu-central-1' },
        })
      );
    const env = devConfig.envName;

    test('should_exportPlaceholderNone_when_imported', () => {
      expect(AGENT_KEY_PLACEHOLDER).toBe('none');
    });

    test('should_createPrivateKeySecretWithPlaceholderAndAwsManagedKey_when_synthesized', () => {
      const t = template();
      const secrets = t.findResources('AWS::SecretsManager::Secret', {
        Properties: { Name: `batbern/${env}/agent/signing-private-key` },
      });
      const [secret] = Object.values(secrets) as { Properties: Record<string, unknown> }[];
      expect(secret.Properties.SecretString).toBe('none');
      expect(secret.Properties.GenerateSecretString).toBeUndefined();
      expect(secret.Properties.KmsKeyId).toBeUndefined();
    });

    test('should_createPublicKeyStringParameterWithPlaceholder_when_synthesized', () => {
      template().hasResourceProperties('AWS::SSM::Parameter', {
        Name: `/batbern/${env}/agent/jwt-public-key`,
        Type: 'String',
        Value: 'none',
      });
    });

    test.each(['ems-caller-secret', 'agent-service-caller-secret'])(
      'should_generateCallerSecretWithAwsManagedKey_when_%sSynthesized',
      (name) => {
        const secrets = template().findResources('AWS::SecretsManager::Secret', {
          Properties: { Name: `batbern/${env}/agent/${name}` },
        });
        const [secret] = Object.values(secrets) as { Properties: Record<string, unknown> }[];
        expect(secret.Properties.GenerateSecretString).toEqual(
          expect.objectContaining({ PasswordLength: 64, ExcludePunctuation: true })
        );
        expect(secret.Properties.SecretString).toBeUndefined();
        expect(secret.Properties.KmsKeyId).toBeUndefined();
      }
    );

    /*
     * Pins the complete deployed Properties of the two key resources, read from the real staging
     * app (bin's stack tags and app tags included). Any change to them makes CloudFormation write
     * `none` back over the real key on the next deploy and silently turns agent tokens off in
     * production. If you change one on purpose: after deploying it, re-run
     * scripts/deploy/create-agent-signing-key.sh --force and force a new deployment of the four
     * services (docs/operations/runbooks.md -> "Agent signing key").
     */
    test('should_keepKeyResourceProperties_when_stagingAppSynthesized', () => {
      const synth = synthStagingApp();
      try {
        const resources = JSON.parse(synth.templates['BATbern-staging-Secrets.template.json'])
          .Resources as Record<string, { Properties: unknown }>;
        const props = (prefix: string) =>
          Object.entries(resources).find(([id]) => id.startsWith(prefix))?.[1].Properties;
        const tags = {
          Component: 'Security',
          Environment: 'staging',
          ManagedBy: 'CDK',
          Project: 'BATbern',
        };
        const hint =
          'Agent key resource properties changed: this deploy resets the key to `none`. ' +
          'After deploying, re-run scripts/deploy/create-agent-signing-key.sh --force (runbook "Agent signing key").';

        expect({ hint, props: props('AgentSigningPrivateKey') }).toEqual({
          hint,
          props: {
            Description:
              'RS256 private key (PKCS#8 PEM) CUMS signs agent tokens with; written by scripts/deploy/create-agent-signing-key.sh',
            Name: 'batbern/staging/agent/signing-private-key',
            SecretString: 'none',
            Tags: Object.entries(tags).map(([Key, Value]) => ({ Key, Value })),
          },
        });
        expect({ hint, props: props('AgentJwtPublicKey') }).toEqual({
          hint,
          props: {
            Description:
              'RSA public key (SPKI PEM) that verifies agent tokens; written by scripts/deploy/create-agent-signing-key.sh',
            Name: '/batbern/staging/agent/jwt-public-key',
            Tags: tags,
            Type: 'String',
            Value: 'none',
          },
        });
      } finally {
        synth.cleanup();
      }
    }, 300000);
  });
});
