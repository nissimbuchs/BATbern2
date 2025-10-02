import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';

export interface RdsClusterProps {
  vpc: ec2.IVpc;
  securityGroup: ec2.ISecurityGroup;
  instanceClass: ec2.InstanceClass;
  instanceSize: ec2.InstanceSize;
  multiAz: boolean;
  backupRetention: cdk.Duration;
  allocatedStorage: number;
  deletionProtection: boolean;
  envName: string;
}

/**
 * Reusable RDS PostgreSQL construct with standardized configuration
 *
 * Features:
 * - PostgreSQL 15.4
 * - Automated backups with configurable retention
 * - Parameter group with optimized settings
 * - Storage encryption at rest
 * - Placement in isolated subnets
 */
export class RdsCluster extends Construct {
  public readonly database: rds.DatabaseInstance;
  public readonly subnetGroup: rds.SubnetGroup;

  constructor(scope: Construct, id: string, props: RdsClusterProps) {
    super(scope, id);

    const isProd = props.envName === 'production';

    // Create DB Subnet Group in isolated subnets
    this.subnetGroup = new rds.SubnetGroup(this, 'SubnetGroup', {
      vpc: props.vpc,
      description: 'Subnet group for RDS PostgreSQL',
      vpcSubnets: {
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      },
    });

    // Create optimized parameter group
    const parameterGroup = new rds.ParameterGroup(this, 'ParameterGroup', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_15_4,
      }),
      parameters: {
        'shared_preload_libraries': 'pg_stat_statements',
        'log_statement': 'all',
        'log_duration': 'on',
        'log_min_duration_statement': '1000', // Log queries > 1 second
        'max_connections': isProd ? '200' : '100',
        'work_mem': isProd ? '32768' : '16384', // In KB: 32MB = 32768 KB, 16MB = 16384 KB
      },
      description: `PostgreSQL parameter group for ${props.envName}`,
    });

    // Create RDS instance
    this.database = new rds.DatabaseInstance(this, 'Instance', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_15_4,
      }),
      instanceType: ec2.InstanceType.of(
        props.instanceClass,
        props.instanceSize
      ),
      vpc: props.vpc,
      subnetGroup: this.subnetGroup,
      securityGroups: [props.securityGroup],
      multiAz: props.multiAz,
      allocatedStorage: props.allocatedStorage,
      storageType: rds.StorageType.GP3,
      storageEncrypted: true,
      backupRetention: props.backupRetention,
      preferredBackupWindow: '03:00-04:00',
      preferredMaintenanceWindow: 'sun:04:00-sun:05:00',
      deletionProtection: props.deletionProtection,
      removalPolicy: isProd
        ? cdk.RemovalPolicy.SNAPSHOT
        : cdk.RemovalPolicy.DESTROY,
      databaseName: 'batbern',
      parameterGroup,
      enablePerformanceInsights: isProd,
      performanceInsightRetention: isProd
        ? rds.PerformanceInsightRetention.DEFAULT
        : undefined,
    });

    // Apply tags
    cdk.Tags.of(this.database).add('Environment', props.envName);
    cdk.Tags.of(this.database).add('Component', 'Database');
  }
}
