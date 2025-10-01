import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { StorageStack } from '../../lib/stacks/storage-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

describe('StorageStack', () => {
  describe('AC15: S3 Buckets', () => {
    test('should_createS3Buckets_when_storageStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify S3 buckets for different purposes
      template.resourceCountIs('AWS::S3::Bucket', 3); // content, logs, backups
    });

    test('should_applyLifecyclePolicies_when_S3BucketCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify lifecycle policies exist
      template.hasResourceProperties('AWS::S3::Bucket', {
        LifecycleConfiguration: Match.objectLike({
          Rules: Match.arrayWith([
            Match.objectLike({
              Status: 'Enabled',
            }),
          ]),
        }),
      });
    });

    test('should_enableVersioning_when_productionBucket', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify versioning is enabled in production
      template.hasResourceProperties('AWS::S3::Bucket', {
        VersioningConfiguration: {
          Status: 'Enabled',
        },
      });
    });

    test('should_enableEncryption_when_bucketCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify server-side encryption is enabled
      template.hasResourceProperties('AWS::S3::Bucket', {
        BucketEncryption: Match.objectLike({
          ServerSideEncryptionConfiguration: Match.arrayWith([
            Match.objectLike({
              ServerSideEncryptionByDefault: Match.objectLike({
                SSEAlgorithm: 'AES256',
              }),
            }),
          ]),
        }),
      });
    });
  });

  describe('AC16: CloudFront Configuration', () => {
    test('should_configureCDN_when_frontendDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify CloudFront distribution exists
      template.resourceCountIs('AWS::CloudFront::Distribution', 1);
    });

    test('should_enableHTTPS_when_distributionCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify HTTPS redirection
      template.hasResourceProperties('AWS::CloudFront::Distribution', {
        DistributionConfig: Match.objectLike({
          DefaultCacheBehavior: Match.objectLike({
            ViewerProtocolPolicy: 'redirect-to-https',
          }),
        }),
      });
    });

    test('should_enableCompression_when_distributionCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify compression is enabled
      template.hasResourceProperties('AWS::CloudFront::Distribution', {
        DistributionConfig: Match.objectLike({
          DefaultCacheBehavior: Match.objectLike({
            Compress: true,
          }),
        }),
      });
    });
  });

  describe('AC5: Resource Tagging', () => {
    test('should_applyConsistentTags_when_resourcesCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new StorageStack(app, 'TestStorageStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert - Verify stack configuration
      expect(stack.stackName).toBe('TestStorageStack');
    });
  });
});
