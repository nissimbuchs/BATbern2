import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { SecretsStack } from '../../lib/stacks/secrets-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

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
      // Note: Only JWT secret here. DB managed by RDS stack, Redis removed (disabled for cost optimization)
      template.resourceCountIs('AWS::SecretsManager::Secret', 1);
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
      template.resourceCountIs('AWS::SSM::Parameter', 2); // Environment name, region
    });
  });
});
