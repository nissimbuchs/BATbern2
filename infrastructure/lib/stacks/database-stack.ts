import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';
import { RdsCluster } from '../constructs/rds-cluster';

export interface DatabaseStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  vpc: ec2.IVpc;
  databaseSecurityGroup: ec2.ISecurityGroup;
  cacheSecurityGroup?: ec2.ISecurityGroup;
}

/**
 * Database Stack - Provides RDS PostgreSQL and ElastiCache Redis infrastructure
 *
 * Implements:
 * - AC6: RDS Configuration with Multi-AZ support
 * - AC8: Backup Strategy with automated backups
 * - AC14: Redis Clusters with ElastiCache
 * - AC4: Security Boundaries with isolated subnets
 */
export class DatabaseStack extends cdk.Stack {
  public readonly database: rds.DatabaseInstance;
  public readonly cacheCluster?: elasticache.CfnReplicationGroup;
  public readonly databaseEndpoint: string;
  public readonly cacheEndpoint?: string;

  constructor(scope: Construct, id: string, props: DatabaseStackProps) {
    super(scope, id, props);

    // Use RDS construct for standardized database creation
    const rdsCluster = new RdsCluster(this, 'RdsCluster', {
      vpc: props.vpc,
      securityGroup: props.databaseSecurityGroup,
      instanceClass: props.config.rds.instanceClass,
      instanceSize: props.config.rds.instanceSize,
      multiAz: props.config.rds.multiAz,
      backupRetention: props.config.rds.backupRetention,
      allocatedStorage: props.config.rds.allocatedStorage,
      deletionProtection: props.config.rds.deletionProtection,
      envName: props.config.envName,
    });

    // Expose database instance
    this.database = rdsCluster.database;
    this.databaseEndpoint = this.database.dbInstanceEndpointAddress;

    // Create ElastiCache Redis cluster only if enabled (numNodes > 0)
    // For production with 1000 users, Redis is disabled for cost optimization
    // Application uses in-memory caching instead
    const isRedisEnabled = props.cacheSecurityGroup && props.config.elasticache.numNodes > 0;

    if (isRedisEnabled) {
      // Create cache subnet group
      const cacheSubnetGroup = new elasticache.CfnSubnetGroup(this, 'CacheSubnetGroup', {
        description: 'Subnet group for ElastiCache Redis',
        subnetIds: props.vpc.selectSubnets({
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        }).subnetIds,
      });

      // Create Redis replication group
      this.cacheCluster = new elasticache.CfnReplicationGroup(this, 'RedisCluster', {
        replicationGroupDescription: 'BATbern Redis Cache Cluster',
        engine: 'redis',
        engineVersion: '7.0', // Changed from 7.2 to 7.0 (7.2 not available in eu-central-1)
        cacheNodeType: props.config.elasticache.nodeType,
        numNodeGroups: 1,
        replicasPerNodeGroup: props.config.elasticache.numNodes - 1,
        automaticFailoverEnabled: props.config.elasticache.automaticFailoverEnabled,
        multiAzEnabled: props.config.elasticache.automaticFailoverEnabled,
        cacheSubnetGroupName: cacheSubnetGroup.ref,
        securityGroupIds: [props.cacheSecurityGroup.securityGroupId],
        atRestEncryptionEnabled: true,
        transitEncryptionEnabled: true,
        snapshotRetentionLimit: props.config.elasticache.snapshotRetentionLimit,
        snapshotWindow: '03:00-05:00',
        preferredMaintenanceWindow: 'sun:05:00-sun:07:00',
        tags: [
          { key: 'Environment', value: props.config.envName },
          { key: 'Service', value: 'Cache' },
          { key: 'Project', value: 'BATbern' },
        ],
      });

      this.cacheEndpoint = this.cacheCluster.attrPrimaryEndPointAddress;
    }

    // Apply tags
    cdk.Tags.of(this).add('Environment', props.config.envName);
    cdk.Tags.of(this).add('Component', 'Database');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'DatabaseEndpoint', {
      value: this.database.dbInstanceEndpointAddress,
      description: 'RDS PostgreSQL endpoint',
      exportName: `${props.config.envName}-DatabaseEndpoint`,
    });

    new cdk.CfnOutput(this, 'DatabasePort', {
      value: this.database.dbInstanceEndpointPort,
      description: 'RDS PostgreSQL port',
      exportName: `${props.config.envName}-DatabasePort`,
    });

    new cdk.CfnOutput(this, 'DatabaseName', {
      value: 'batbern',
      description: 'RDS PostgreSQL database name',
      exportName: `${props.config.envName}-DatabaseName`,
    });

    // Database credentials secret (automatically created by CDK)
    new cdk.CfnOutput(this, 'DatabaseSecretArn', {
      value: this.database.secret?.secretArn || 'N/A',
      description: 'ARN of the Secrets Manager secret containing database credentials',
      exportName: `${props.config.envName}-DatabaseSecretArn`,
    });

    new cdk.CfnOutput(this, 'DatabaseSecretName', {
      value: this.database.secret?.secretName || 'N/A',
      description: 'Name of the Secrets Manager secret containing database credentials',
    });

    // Full JDBC URL for convenience
    new cdk.CfnOutput(this, 'DatabaseJdbcUrl', {
      value: `jdbc:postgresql://${this.database.dbInstanceEndpointAddress}:${this.database.dbInstanceEndpointPort}/batbern`,
      description: 'JDBC connection string for the database',
    });

    if (this.cacheCluster) {
      new cdk.CfnOutput(this, 'CacheEndpoint', {
        value: this.cacheCluster.attrPrimaryEndPointAddress || 'pending',
        description: 'ElastiCache Redis endpoint',
        exportName: `${props.config.envName}-CacheEndpoint`,
      });
    }
  }
}
