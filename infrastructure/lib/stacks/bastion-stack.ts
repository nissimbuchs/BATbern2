import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface BastionStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
}

/**
 * Bastion Stack - Provides secure access to private resources via AWS Systems Manager
 *
 * Implements:
 * - AC: Secure bastion host for database access during development
 * - AC: SSM Session Manager for secure shell access without SSH keys
 * - AC: Security group allowing bastion to access RDS
 *
 * Usage:
 * 1. Deploy this stack to create bastion host
 * 2. Use SSM Session Manager to create port forward tunnel:
 *    aws ssm start-session --target <instance-id> \
 *      --document-name AWS-StartPortForwardingSessionToRemoteHost \
 *      --parameters '{"host":["<rds-endpoint>"],"portNumber":["5432"],"localPortNumber":["5432"]}'
 * 3. Update .env to use DB_HOST=localhost
 */
export class BastionStack extends cdk.Stack {
  public readonly bastionInstance: ec2.Instance;
  public readonly bastionSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: BastionStackProps) {
    super(scope, id, props);

    // Create security group for bastion host
    this.bastionSecurityGroup = new ec2.SecurityGroup(this, 'BastionSG', {
      vpc: props.vpc,
      description: 'Security group for bastion host (SSM access only, no SSH)',
      allowAllOutbound: true, // Needed for SSM and RDS access
    });

    // Note: Database security group ingress rule will be added manually or via separate stack
    // to avoid circular dependency between Network and Bastion stacks.
    // After bastion is deployed, add this rule to database security group:
    // aws ec2 authorize-security-group-ingress --group-id <db-sg-id> \
    //   --protocol tcp --port 5432 --source-group <bastion-sg-id>

    // Create IAM role for bastion with SSM permissions
    const bastionRole = new iam.Role(this, 'BastionRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      description: 'IAM role for bastion host with SSM access',
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
      ],
    });

    // Use latest Amazon Linux 2023 AMI (optimized for SSM)
    const ami = ec2.MachineImage.latestAmazonLinux2023({
      cpuType: ec2.AmazonLinuxCpuType.ARM_64, // Graviton for cost savings
    });

    // Create bastion instance in public subnet
    this.bastionInstance = new ec2.Instance(this, 'BastionInstance', {
      vpc: props.vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC, // Public subnet for SSM endpoint access
      },
      instanceType: ec2.InstanceType.of(
        ec2.InstanceClass.T4G, // ARM-based Graviton
        ec2.InstanceSize.NANO // Smallest size for cost optimization
      ),
      machineImage: ami,
      securityGroup: this.bastionSecurityGroup,
      role: bastionRole,
      ssmSessionPermissions: true, // Enable SSM Session Manager
      requireImdsv2: true, // Security best practice
    });

    // Add tags
    cdk.Tags.of(this.bastionInstance).add('Name', `${props.config.envName}-bastion`);
    cdk.Tags.of(this.bastionInstance).add('Environment', props.config.envName);
    cdk.Tags.of(this.bastionInstance).add('Purpose', 'Database Access');
    cdk.Tags.of(this).add('Component', 'Bastion');

    // Outputs
    new cdk.CfnOutput(this, 'BastionInstanceId', {
      value: this.bastionInstance.instanceId,
      description: 'Bastion instance ID for SSM Session Manager',
      exportName: `${props.config.envName}-BastionInstanceId`,
    });

    new cdk.CfnOutput(this, 'BastionSecurityGroupId', {
      value: this.bastionSecurityGroup.securityGroupId,
      description: 'Bastion security group ID (add to database security group)',
      exportName: `${props.config.envName}-BastionSecurityGroupId`,
    });

    new cdk.CfnOutput(this, 'AddDatabaseAccessCommand', {
      value: `aws ec2 authorize-security-group-ingress --group-id <DB_SG_ID> --protocol tcp --port 5432 --source-group ${this.bastionSecurityGroup.securityGroupId} --region ${props.config.region}`,
      description: 'Run this command to allow bastion to access database (replace DB_SG_ID)',
    });

    new cdk.CfnOutput(this, 'SSMTunnelCommand', {
      value: `aws ssm start-session --target ${this.bastionInstance.instanceId} --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters '{"host":["YOUR_RDS_ENDPOINT"],"portNumber":["5432"],"localPortNumber":["5432"]}' --region ${props.config.region}`,
      description: 'Command to create SSM tunnel to RDS (replace YOUR_RDS_ENDPOINT)',
    });

    new cdk.CfnOutput(this, 'BastionSetupInstructions', {
      value: 'https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html',
      description: 'Install Session Manager plugin for port forwarding',
    });
  }
}
