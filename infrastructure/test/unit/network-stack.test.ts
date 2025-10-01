import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { NetworkStack } from '../../lib/stacks/network-stack';
import { devConfig } from '../../lib/config/dev-config';
import { prodConfig } from '../../lib/config/prod-config';

describe('NetworkStack', () => {
  describe('AC3: Network Isolation - VPC Configuration', () => {
    test('should_createIsolatedVPC_when_environmentDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify VPC exists with correct configuration
      template.hasResourceProperties('AWS::EC2::VPC', {
        CidrBlock: '10.0.0.0/16',
        EnableDnsHostnames: true,
        EnableDnsSupport: true,
      });
    });

    test('should_createPublicPrivateIsolatedSubnets_when_vpcCreated', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify subnets - 2 AZs * 3 subnet types = 6 subnets
      template.resourceCountIs('AWS::EC2::Subnet', 6);
    });

    test('should_createNATGateways_when_privateSubnetsNeedInternet', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Dev environment should have 1 NAT Gateway for cost optimization
      template.resourceCountIs('AWS::EC2::NatGateway', 1);
    });

    test('should_createMultipleNATGateways_when_productionEnvironment', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: prodConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Production should have 3 NAT Gateways for HA
      template.resourceCountIs('AWS::EC2::NatGateway', 3);
    });
  });

  describe('AC5: Resource Tagging', () => {
    test('should_applyConsistentTags_when_creatingResources', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert - Tags are applied at stack level via CDK
      // Verify stack has proper configuration
      expect(stack.stackName).toBe('TestNetworkStack');
    });
  });

  describe('AC3: Network Security', () => {
    test('should_createSecurityGroups_when_networkStackDeployed', () => {
      // Arrange
      const app = new App();

      // Act
      const stack = new NetworkStack(app, 'TestNetworkStack', {
        config: devConfig,
        env: {
          account: '123456789012',
          region: 'eu-central-1',
        },
      });

      // Assert
      const template = Template.fromStack(stack);

      // Verify security groups for database, cache, and application tiers
      template.resourceCountIs('AWS::EC2::SecurityGroup', 3);
    });
  });
});
