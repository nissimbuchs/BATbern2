import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import { EnvironmentConfig } from '../config/environment-config';

export interface CICDStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  /**
   * GitHub repository in format: owner/repo (e.g., "myorg/BATbern")
   * Used for OIDC trust relationship
   */
  githubRepository: string;
}

/**
 * CI/CD Infrastructure Stack
 *
 * Provisions AWS resources required for GitHub Actions CI/CD pipeline:
 * - ECR repositories for all services
 * - IAM roles for GitHub Actions (OIDC)
 * - CloudWatch log groups for pipeline logs
 */
export class CICDStack extends cdk.Stack {
  public readonly ecrRepositories: Map<string, ecr.Repository>;
  public readonly githubActionsRole: iam.Role;

  constructor(scope: Construct, id: string, props: CICDStackProps) {
    super(scope, id, props);

    const { config, githubRepository } = props;

    // Service names that need ECR repositories
    const services = [
      'shared-kernel',
      'api-gateway',
      'event-management-service',
      'speaker-coordination-service',
      'partner-coordination-service',
      'attendee-experience-service',
      'company-user-management-service',
    ];

    this.ecrRepositories = new Map();

    // ═══════════════════════════════════════════════════════════
    // ECR REPOSITORIES
    // ═══════════════════════════════════════════════════════════

    services.forEach(serviceName => {
      const repository = new ecr.Repository(this, `${serviceName}-repo`, {
        repositoryName: `batbern/${config.envName}/${serviceName}`,
        imageScanOnPush: true,
        encryption: ecr.RepositoryEncryption.AES_256,
        lifecycleRules: [
          {
            description: 'Remove untagged images after 7 days',
            maxImageAge: cdk.Duration.days(7),
            tagStatus: ecr.TagStatus.UNTAGGED,
            rulePriority: 1,
          },
          {
            description: 'Keep only last 10 images',
            maxImageCount: 10,
            rulePriority: 2,
          },
        ],
        removalPolicy: config.envName === 'production'
          ? cdk.RemovalPolicy.RETAIN
          : cdk.RemovalPolicy.DESTROY,
      });

      // Add tags
      cdk.Tags.of(repository).add('Service', serviceName);
      cdk.Tags.of(repository).add('ManagedBy', 'CDK');

      this.ecrRepositories.set(serviceName, repository);

      // Output repository URIs for easy reference
      new cdk.CfnOutput(this, `${serviceName}-repository-uri`, {
        value: repository.repositoryUri,
        description: `ECR repository URI for ${serviceName}`,
        exportName: `${config.envName}-${serviceName}-repository-uri`,
      });
    });

    // ═══════════════════════════════════════════════════════════
    // GITHUB ACTIONS OIDC PROVIDER
    // ═══════════════════════════════════════════════════════════

    // Check if OIDC provider already exists (can only have one per account)
    // This is a known limitation - we reference it by ARN
    const githubOidcProviderArn = `arn:aws:iam::${this.account}:oidc-provider/token.actions.githubusercontent.com`;

    // Note: OIDC provider must be created manually once per AWS account:
    // aws iam create-open-id-connect-provider \
    //   --url https://token.actions.githubusercontent.com \
    //   --client-id-list sts.amazonaws.com \
    //   --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

    // ═══════════════════════════════════════════════════════════
    // IAM ROLE FOR GITHUB ACTIONS (OIDC)
    // ═══════════════════════════════════════════════════════════

    const githubActionsRole = new iam.Role(this, 'GitHubActionsRole', {
      roleName: `batbern-${config.envName}-github-actions-role`,
      description: `Role for GitHub Actions CI/CD pipeline - ${config.envName}`,
      assumedBy: new iam.WebIdentityPrincipal(
        githubOidcProviderArn,
        {
          StringEquals: {
            'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
          },
          StringLike: {
            'token.actions.githubusercontent.com:sub': `repo:${githubRepository}:*`,
          },
        }
      ),
      maxSessionDuration: cdk.Duration.hours(1),
    });

    // ECR Permissions - Push and pull images
    this.ecrRepositories.forEach(repository => {
      repository.grantPullPush(githubActionsRole);
    });

    // Additional ECR permissions for Docker operations
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ecr:GetAuthorizationToken',
        'ecr:BatchCheckLayerAvailability',
        'ecr:GetDownloadUrlForLayer',
        'ecr:BatchGetImage',
        'ecr:PutImage',
        'ecr:InitiateLayerUpload',
        'ecr:UploadLayerPart',
        'ecr:CompleteLayerUpload',
        'ecr:DescribeRepositories',
        'ecr:ListImages',
      ],
      resources: ['*'], // GetAuthorizationToken requires '*'
    }));

    // ECS Permissions - Deploy services
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ecs:DescribeServices',
        'ecs:DescribeTaskDefinition',
        'ecs:DescribeTasks',
        'ecs:ListTasks',
        'ecs:RegisterTaskDefinition',
        'ecs:UpdateService',
        'ecs:TagResource',
      ],
      resources: ['*'], // Will be refined when ECS services are deployed
    }));

    // RDS Permissions - Create snapshots for backup before deployment
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'rds:CreateDBSnapshot',
        'rds:DescribeDBInstances',
        'rds:DescribeDBSnapshots',
        'rds:ListTagsForResource',
      ],
      resources: [
        `arn:aws:rds:${this.region}:${this.account}:db:batbern-${config.envName}-*`,
        `arn:aws:rds:${this.region}:${this.account}:snapshot:batbern-${config.envName}-*`,
      ],
    }));

    // CloudWatch Logs - Write pipeline logs
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'logs:CreateLogGroup',
        'logs:CreateLogStream',
        'logs:PutLogEvents',
        'logs:DescribeLogGroups',
        'logs:DescribeLogStreams',
      ],
      resources: [
        `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/ecs/batbern-${config.envName}-*`,
      ],
    }));

    // Secrets Manager - Read database credentials for migrations
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'secretsmanager:GetSecretValue',
        'secretsmanager:DescribeSecret',
      ],
      resources: [
        `arn:aws:secretsmanager:${this.region}:${this.account}:secret:batbern/${config.envName}/*`,
      ],
    }));

    // IAM PassRole - Required for ECS task execution
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['iam:PassRole'],
      resources: [
        `arn:aws:iam::${this.account}:role/batbern-${config.envName}-*`,
      ],
      conditions: {
        StringEquals: {
          'iam:PassedToService': 'ecs-tasks.amazonaws.com',
        },
      },
    }));

    // ═══════════════════════════════════════════════════════════
    // CDK DEPLOYMENT PERMISSIONS
    // ═══════════════════════════════════════════════════════════

    // CloudFormation - Deploy and manage CDK stacks
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cloudformation:CreateStack',
        'cloudformation:UpdateStack',
        'cloudformation:DeleteStack',
        'cloudformation:DescribeStacks',
        'cloudformation:DescribeStackEvents',
        'cloudformation:DescribeStackResources',
        'cloudformation:GetTemplate',
        'cloudformation:ListStacks',
        'cloudformation:ListStackResources',
        'cloudformation:ValidateTemplate',
        'cloudformation:CreateChangeSet',
        'cloudformation:DescribeChangeSet',
        'cloudformation:ExecuteChangeSet',
        'cloudformation:DeleteChangeSet',
        'cloudformation:GetTemplateSummary',
      ],
      resources: [
        `arn:aws:cloudformation:${this.region}:${this.account}:stack/BATbern-${config.envName}-*/*`,
        `arn:aws:cloudformation:${this.region}:${this.account}:stack/CDKToolkit/*`,
      ],
    }));

    // S3 - CDK asset bucket and application buckets
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        's3:CreateBucket',
        's3:DeleteBucket',
        's3:PutBucketPolicy',
        's3:DeleteBucketPolicy',
        's3:GetBucketPolicy',
        's3:PutBucketVersioning',
        's3:PutBucketPublicAccessBlock',
        's3:PutBucketEncryption',
        's3:PutBucketLogging',
        's3:PutBucketCors',
        's3:PutObject',
        's3:GetObject',
        's3:DeleteObject',
        's3:ListBucket',
        's3:GetBucketLocation',
        's3:GetBucketVersioning',
        's3:PutLifecycleConfiguration',
      ],
      resources: [
        `arn:aws:s3:::cdk-*-assets-${this.account}-${this.region}`,
        `arn:aws:s3:::cdk-*-assets-${this.account}-${this.region}/*`,
        `arn:aws:s3:::batbern-${config.envName}-*`,
        `arn:aws:s3:::batbern-${config.envName}-*/*`,
      ],
    }));

    // IAM - Create and manage roles, policies for CDK resources
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'iam:CreateRole',
        'iam:DeleteRole',
        'iam:GetRole',
        'iam:UpdateRole',
        'iam:PutRolePolicy',
        'iam:DeleteRolePolicy',
        'iam:GetRolePolicy',
        'iam:AttachRolePolicy',
        'iam:DetachRolePolicy',
        'iam:ListRolePolicies',
        'iam:ListAttachedRolePolicies',
        'iam:CreatePolicy',
        'iam:DeletePolicy',
        'iam:GetPolicy',
        'iam:GetPolicyVersion',
        'iam:ListPolicyVersions',
        'iam:CreatePolicyVersion',
        'iam:DeletePolicyVersion',
        'iam:TagRole',
        'iam:UntagRole',
        'iam:TagPolicy',
        'iam:UntagPolicy',
      ],
      resources: [
        `arn:aws:iam::${this.account}:role/batbern-${config.envName}-*`,
        `arn:aws:iam::${this.account}:role/cdk-*`,
        `arn:aws:iam::${this.account}:policy/batbern-${config.envName}-*`,
      ],
    }));

    // Lambda - CDK custom resources
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'lambda:CreateFunction',
        'lambda:DeleteFunction',
        'lambda:GetFunction',
        'lambda:GetFunctionConfiguration',
        'lambda:UpdateFunctionCode',
        'lambda:UpdateFunctionConfiguration',
        'lambda:InvokeFunction',
        'lambda:ListFunctions',
        'lambda:ListVersionsByFunction',
        'lambda:PublishVersion',
        'lambda:TagResource',
        'lambda:UntagResource',
        'lambda:AddPermission',
        'lambda:RemovePermission',
        'lambda:GetPolicy',
      ],
      resources: [
        `arn:aws:lambda:${this.region}:${this.account}:function:batbern-${config.envName}-*`,
        `arn:aws:lambda:${this.region}:${this.account}:function:cdk-*`,
      ],
    }));

    // VPC and EC2 - Network infrastructure
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ec2:CreateVpc',
        'ec2:DeleteVpc',
        'ec2:DescribeVpcs',
        'ec2:ModifyVpcAttribute',
        'ec2:CreateSubnet',
        'ec2:DeleteSubnet',
        'ec2:DescribeSubnets',
        'ec2:ModifySubnetAttribute',
        'ec2:CreateRouteTable',
        'ec2:DeleteRouteTable',
        'ec2:DescribeRouteTables',
        'ec2:CreateRoute',
        'ec2:DeleteRoute',
        'ec2:AssociateRouteTable',
        'ec2:DisassociateRouteTable',
        'ec2:CreateInternetGateway',
        'ec2:DeleteInternetGateway',
        'ec2:AttachInternetGateway',
        'ec2:DetachInternetGateway',
        'ec2:DescribeInternetGateways',
        'ec2:CreateNatGateway',
        'ec2:DeleteNatGateway',
        'ec2:DescribeNatGateways',
        'ec2:AllocateAddress',
        'ec2:ReleaseAddress',
        'ec2:DescribeAddresses',
        'ec2:CreateSecurityGroup',
        'ec2:DeleteSecurityGroup',
        'ec2:DescribeSecurityGroups',
        'ec2:AuthorizeSecurityGroupIngress',
        'ec2:AuthorizeSecurityGroupEgress',
        'ec2:RevokeSecurityGroupIngress',
        'ec2:RevokeSecurityGroupEgress',
        'ec2:CreateTags',
        'ec2:DeleteTags',
        'ec2:DescribeTags',
        'ec2:DescribeAvailabilityZones',
        'ec2:DescribeAccountAttributes',
        'ec2:DescribeNetworkInterfaces',
        'ec2:CreateNetworkInterface',
        'ec2:DeleteNetworkInterface',
        'ec2:ModifyNetworkInterfaceAttribute',
      ],
      resources: ['*'], // EC2 VPC operations often require '*'
    }));

    // RDS - Database infrastructure (expanded permissions)
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'rds:CreateDBInstance',
        'rds:DeleteDBInstance',
        'rds:ModifyDBInstance',
        'rds:CreateDBCluster',
        'rds:DeleteDBCluster',
        'rds:ModifyDBCluster',
        'rds:CreateDBSubnetGroup',
        'rds:DeleteDBSubnetGroup',
        'rds:DescribeDBSubnetGroups',
        'rds:CreateDBParameterGroup',
        'rds:DeleteDBParameterGroup',
        'rds:DescribeDBParameterGroups',
        'rds:ModifyDBParameterGroup',
        'rds:AddTagsToResource',
        'rds:RemoveTagsFromResource',
        'rds:DescribeDBClusters',
      ],
      resources: [
        `arn:aws:rds:${this.region}:${this.account}:db:batbern-${config.envName}-*`,
        `arn:aws:rds:${this.region}:${this.account}:cluster:batbern-${config.envName}-*`,
        `arn:aws:rds:${this.region}:${this.account}:subgrp:batbern-${config.envName}-*`,
        `arn:aws:rds:${this.region}:${this.account}:pg:batbern-${config.envName}-*`,
      ],
    }));

    // ElastiCache - Redis infrastructure
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'elasticache:CreateCacheCluster',
        'elasticache:DeleteCacheCluster',
        'elasticache:ModifyCacheCluster',
        'elasticache:DescribeCacheClusters',
        'elasticache:CreateReplicationGroup',
        'elasticache:DeleteReplicationGroup',
        'elasticache:ModifyReplicationGroup',
        'elasticache:DescribeReplicationGroups',
        'elasticache:CreateCacheSubnetGroup',
        'elasticache:DeleteCacheSubnetGroup',
        'elasticache:DescribeCacheSubnetGroups',
        'elasticache:CreateCacheParameterGroup',
        'elasticache:DeleteCacheParameterGroup',
        'elasticache:DescribeCacheParameterGroups',
        'elasticache:AddTagsToResource',
        'elasticache:RemoveTagsFromResource',
      ],
      resources: [
        `arn:aws:elasticache:${this.region}:${this.account}:cluster:batbern-${config.envName}-*`,
        `arn:aws:elasticache:${this.region}:${this.account}:replicationgroup:batbern-${config.envName}-*`,
        `arn:aws:elasticache:${this.region}:${this.account}:subnetgroup:batbern-${config.envName}-*`,
        `arn:aws:elasticache:${this.region}:${this.account}:parametergroup:batbern-${config.envName}-*`,
      ],
    }));

    // Cognito - User authentication
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cognito-idp:CreateUserPool',
        'cognito-idp:DeleteUserPool',
        'cognito-idp:UpdateUserPool',
        'cognito-idp:DescribeUserPool',
        'cognito-idp:CreateUserPoolClient',
        'cognito-idp:DeleteUserPoolClient',
        'cognito-idp:UpdateUserPoolClient',
        'cognito-idp:DescribeUserPoolClient',
        'cognito-idp:CreateUserPoolDomain',
        'cognito-idp:DeleteUserPoolDomain',
        'cognito-idp:DescribeUserPoolDomain',
        'cognito-idp:CreateGroup',
        'cognito-idp:DeleteGroup',
        'cognito-idp:SetUserPoolMfaConfig',
        'cognito-idp:TagResource',
        'cognito-idp:UntagResource',
      ],
      resources: [
        `arn:aws:cognito-idp:${this.region}:${this.account}:userpool/*`,
      ],
    }));

    // CloudFront - CDN for frontend
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cloudfront:CreateDistribution',
        'cloudfront:GetDistribution',
        'cloudfront:UpdateDistribution',
        'cloudfront:DeleteDistribution',
        'cloudfront:TagResource',
        'cloudfront:UntagResource',
        'cloudfront:CreateOriginAccessControl',
        'cloudfront:GetOriginAccessControl',
        'cloudfront:UpdateOriginAccessControl',
        'cloudfront:DeleteOriginAccessControl',
        'cloudfront:CreateInvalidation',
      ],
      resources: ['*'], // CloudFront doesn't support resource-level permissions
    }));

    // Secrets Manager - Expanded permissions
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'secretsmanager:CreateSecret',
        'secretsmanager:DeleteSecret',
        'secretsmanager:UpdateSecret',
        'secretsmanager:PutSecretValue',
        'secretsmanager:TagResource',
        'secretsmanager:UntagResource',
        'secretsmanager:RotateSecret',
      ],
      resources: [
        `arn:aws:secretsmanager:${this.region}:${this.account}:secret:batbern/${config.envName}/*`,
      ],
    }));

    // KMS - Encryption keys
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'kms:CreateKey',
        'kms:DescribeKey',
        'kms:EnableKeyRotation',
        'kms:PutKeyPolicy',
        'kms:CreateAlias',
        'kms:DeleteAlias',
        'kms:UpdateAlias',
        'kms:TagResource',
        'kms:UntagResource',
        'kms:ScheduleKeyDeletion',
        'kms:Encrypt',
        'kms:Decrypt',
        'kms:GenerateDataKey',
      ],
      resources: [
        `arn:aws:kms:${this.region}:${this.account}:key/*`,
        `arn:aws:kms:${this.region}:${this.account}:alias/batbern-${config.envName}-*`,
      ],
    }));

    // SSM Parameter Store - Configuration parameters
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ssm:PutParameter',
        'ssm:GetParameter',
        'ssm:GetParameters',
        'ssm:DeleteParameter',
        'ssm:DescribeParameters',
        'ssm:AddTagsToResource',
        'ssm:RemoveTagsFromResource',
      ],
      resources: [
        `arn:aws:ssm:${this.region}:${this.account}:parameter/batbern/${config.envName}/*`,
      ],
    }));

    // CloudWatch - Alarms, dashboards, and additional log permissions
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cloudwatch:PutMetricAlarm',
        'cloudwatch:DeleteAlarms',
        'cloudwatch:DescribeAlarms',
        'cloudwatch:PutDashboard',
        'cloudwatch:GetDashboard',
        'cloudwatch:DeleteDashboards',
        'cloudwatch:ListDashboards',
        'logs:DeleteLogGroup',
        'logs:PutRetentionPolicy',
        'logs:DeleteRetentionPolicy',
        'logs:TagLogGroup',
        'logs:UntagLogGroup',
      ],
      resources: [
        `arn:aws:cloudwatch:${this.region}:${this.account}:alarm:batbern-${config.envName}-*`,
        `arn:aws:logs:${this.region}:${this.account}:log-group:*`,
      ],
    }));

    // ACM - SSL/TLS Certificates
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'acm:RequestCertificate',
        'acm:DescribeCertificate',
        'acm:DeleteCertificate',
        'acm:AddTagsToCertificate',
        'acm:RemoveTagsFromCertificate',
        'acm:ListCertificates',
        'acm:GetCertificate',
      ],
      resources: ['*'], // ACM doesn't support resource-level permissions for some actions
    }));

    // Route53 - DNS management
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'route53:CreateHostedZone',
        'route53:GetHostedZone',
        'route53:DeleteHostedZone',
        'route53:ListHostedZones',
        'route53:ChangeResourceRecordSets',
        'route53:GetChange',
        'route53:ListResourceRecordSets',
        'route53:ChangeTagsForResource',
      ],
      resources: [
        `arn:aws:route53:::hostedzone/*`,
        `arn:aws:route53:::change/*`,
      ],
    }));

    // SSO and pass role for various services
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'iam:PassRole',
      ],
      resources: [
        `arn:aws:iam::${this.account}:role/batbern-${config.envName}-*`,
        `arn:aws:iam::${this.account}:role/cdk-*`,
      ],
      conditions: {
        StringEquals: {
          'iam:PassedToService': [
            'cloudformation.amazonaws.com',
            'lambda.amazonaws.com',
            'ecs-tasks.amazonaws.com',
          ],
        },
      },
    }));

    // ECS Cluster management (expanded)
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ecs:CreateCluster',
        'ecs:DeleteCluster',
        'ecs:DescribeClusters',
        'ecs:CreateService',
        'ecs:DeleteService',
        'ecs:DeregisterTaskDefinition',
        'ecs:TagResource',
        'ecs:UntagResource',
      ],
      resources: ['*'],
    }));

    // Application Auto Scaling
    githubActionsRole.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'application-autoscaling:RegisterScalableTarget',
        'application-autoscaling:DeregisterScalableTarget',
        'application-autoscaling:DescribeScalableTargets',
        'application-autoscaling:PutScalingPolicy',
        'application-autoscaling:DeleteScalingPolicy',
        'application-autoscaling:DescribeScalingPolicies',
      ],
      resources: ['*'],
    }));

    this.githubActionsRole = githubActionsRole;

    // ═══════════════════════════════════════════════════════════
    // CLOUDWATCH LOG GROUPS
    // ═══════════════════════════════════════════════════════════

    // Log group for CI/CD pipeline logs
    const pipelineLogGroup = new cdk.aws_logs.LogGroup(this, 'PipelineLogGroup', {
      logGroupName: `/aws/cicd/BATbern-${config.envName}/pipeline`,
      retention: config.envName === 'production'
        ? cdk.aws_logs.RetentionDays.ONE_MONTH
        : cdk.aws_logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ═══════════════════════════════════════════════════════════
    // OUTPUTS
    // ═══════════════════════════════════════════════════════════

    new cdk.CfnOutput(this, 'GitHubActionsRoleArn', {
      value: githubActionsRole.roleArn,
      description: 'IAM Role ARN for GitHub Actions OIDC',
      exportName: `${config.envName}-github-actions-role-arn`,
    });

    new cdk.CfnOutput(this, 'GitHubActionsRoleName', {
      value: githubActionsRole.roleName,
      description: 'IAM Role Name for GitHub Actions',
    });

    new cdk.CfnOutput(this, 'PipelineLogGroupName', {
      value: pipelineLogGroup.logGroupName,
      description: 'CloudWatch Log Group for CI/CD pipeline logs',
    });

    new cdk.CfnOutput(this, 'ECRRegistryUrl', {
      value: `${this.account}.dkr.ecr.${this.region}.amazonaws.com`,
      description: 'ECR Registry URL',
      exportName: `${config.envName}-ecr-registry-url`,
    });
  }
}
