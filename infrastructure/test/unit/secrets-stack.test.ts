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
      template.resourceCountIs('AWS::SecretsManager::Secret', 3); // DB, Redis, JWT
    });

    test('should_rotateSecrets_when_credentialsStored', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new SecretsStack(app, 'TestSecretsStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify rotation schedule exists for database secret
      template.resourceCountIs('AWS::SecretsManager::RotationSchedule', 1);
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

      // Verify secret has generation configuration
      template.hasResourceProperties('AWS::SecretsManager::Secret', {
        GenerateSecretString: Match.objectLike({
          PasswordLength: 32,
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
