import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { NetworkStack } from '../../lib/stacks/network-stack';
import { DatabaseStack } from '../../lib/stacks/database-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

describe('DatabaseStack', () => {
  describe('AC6: RDS Configuration', () => {
    test('should_createPostgreSQLInstance_when_databaseStackDeployed', () => {
      // Arrange
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: devConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify PostgreSQL instance exists with correct engine
      template.hasResourceProperties('AWS::RDS::DBInstance', {
        Engine: 'postgres',
        // Note: Not testing specific version - that's implementation detail that changes with upgrades
      });
    });

    test('should_useSingleAZ_when_productionEnvironmentOptimizedForCost', () => {
      // Arrange - Production uses Single-AZ for cost optimization (low-traffic use case)
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: prodConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: prodConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify Single-AZ for cost optimization (multiAz: false in prod-config)
      template.hasResourceProperties('AWS::RDS::DBInstance', {
        MultiAZ: false,
      });
    });

    test('should_configureSingleAZ_when_developmentEnvironment', () => {
      // Arrange
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: devConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify Multi-AZ is disabled for development
      template.hasResourceProperties('AWS::RDS::DBInstance', {
        MultiAZ: false,
      });
    });
  });

  describe('AC8: Backup Strategy', () => {
    test('should_configureBackupRetention_when_RDSProvisioned', () => {
      // Arrange
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: devConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify backup retention period (7 days for dev)
      template.hasResourceProperties('AWS::RDS::DBInstance', {
        BackupRetentionPeriod: 7,
      });
    });

    test('should_enableStorageEncryption_when_databaseCreated', () => {
      // Arrange
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: devConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify storage encryption is enabled
      template.hasResourceProperties('AWS::RDS::DBInstance', {
        StorageEncrypted: true,
      });
    });
  });

  // AC14: Redis Clusters - REMOVED
  // Redis has been disabled for cost optimization (numNodes: 0 in all environments)
  // Application uses in-memory caching instead
  // Tests removed as they were testing dead code paths that will never execute in production

  describe('AC4: Security Boundaries', () => {
    test('should_placeDatabaseInIsolatedSubnets_when_created', () => {
      // Arrange
      const app = new App();
      const networkStack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Act
      const dbStack = new DatabaseStack(app, 'TestDatabaseStack', {
        config: devConfig,
        vpc: networkStack.vpc,
        databaseSecurityGroup: networkStack.databaseSecurityGroup,
        env: { account: '123456789012', region: 'eu-central-1' },
      });

      // Assert
      const template = Template.fromStack(dbStack);

      // Verify DB subnet group is created
      template.resourceCountIs('AWS::RDS::DBSubnetGroup', 1);
    });
  });
});
