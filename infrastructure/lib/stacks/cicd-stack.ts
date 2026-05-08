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
 *
 * IAM permissions are split into two explicit ManagedPolicies:
 *
 *   WorkflowRuntimePolicy — every `aws` CLI call made directly by workflow YAML steps
 *     (deploy-staging.yml, deploy-code-staging.yml, update-ecs-task.sh).
 *     Keep this short; every action here MUST have a test in cicd-stack.test.ts.
 *
 *   CdkDeploymentPolicy — everything `cdk deploy` needs to provision and manage
 *     infrastructure via CloudFormation + CDK bootstrap roles.
 *
 * How to keep WorkflowRuntimePolicy in sync with the workflow YAML:
 *   grep -oP 'aws \K[a-z0-9-]+ [a-z0-9-]+' .github/workflows/deploy-staging.yml | sort -u
 *   Map each CLI sub-command to its IAM action and add a test to cicd-stack.test.ts.
 *   The pre-push hook enforces this automatically when either file changes.
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
            // Increased from 10 to 30: sequential deploys (6 services × up to 25 min each)
            // can cause older rollback images to be purged before they're needed.
            // 30 images ≈ ~$1.80/month extra storage (negligible).
            description: 'Keep last 30 images for safe rollbacks',
            maxImageCount: 30,
            rulePriority: 2,
          },
        ],
        removalPolicy: (config.isProduction ?? (config.envName === 'production'))
          ? cdk.RemovalPolicy.RETAIN
          : cdk.RemovalPolicy.DESTROY,
      });

      cdk.Tags.of(repository).add('Service', serviceName);
      cdk.Tags.of(repository).add('ManagedBy', 'CDK');
      this.ecrRepositories.set(serviceName, repository);

      new cdk.CfnOutput(this, `${serviceName}-repository-uri`, {
        value: repository.repositoryUri,
        description: `ECR repository URI for ${serviceName}`,
        exportName: `${config.envName}-${serviceName}-repository-uri`,
      });
    });

    // ═══════════════════════════════════════════════════════════
    // GITHUB ACTIONS OIDC PROVIDER
    // ═══════════════════════════════════════════════════════════

    // Note: OIDC provider must be created manually once per AWS account:
    // aws iam create-open-id-connect-provider \
    //   --url https://token.actions.githubusercontent.com \
    //   --client-id-list sts.amazonaws.com \
    //   --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
    const githubOidcProviderArn = `arn:aws:iam::${this.account}:oidc-provider/token.actions.githubusercontent.com`;

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

    // ═══════════════════════════════════════════════════════════
    // WORKFLOW RUNTIME POLICY
    //
    // Every action here maps directly to an `aws` CLI call in a workflow YAML
    // step. Each action MUST have a corresponding test in cicd-stack.test.ts.
    // The pre-push hook will fail if you add a new `aws` call to the workflow
    // without also adding a test here.
    // ═══════════════════════════════════════════════════════════

    const workflowRuntimePolicy = new iam.ManagedPolicy(this, 'WorkflowRuntimePolicy', {
      managedPolicyName: `batbern-${config.envName}-github-workflow-runtime`,
      description: 'GitHub Actions runtime — actions called directly by workflow YAML steps',
      document: new iam.PolicyDocument({
        statements: [

          // ECS — stabilize wait, stack-status checks, fast-path deploy, IAM simulation gate
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'ecs:DescribeClusters',       // wait-for-stabilize guard, update-ecs-task.sh
              'ecs:DescribeServices',       // wait-for-stabilize
              'ecs:DescribeTaskDefinition', // simulate-principal-policy gate
              'ecs:ListServices',           // fast-path deployment
              'ecs:ListTaskDefinitions',    // simulate-principal-policy gate
              'ecs:RegisterTaskDefinition', // fast-path deployment
              'ecs:UpdateService',          // fast-path deployment
            ],
            resources: ['*'],
          }),

          // IAM — post-deploy simulate-principal-policy gate (read-only, no side-effects)
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['iam:SimulatePrincipalPolicy'],
            resources: [
              `arn:aws:iam::${this.account}:role/BATbern-${config.envName}-*`,
              `arn:aws:iam::${this.account}:role/cdk-*`,
            ],
          }),

          // CloudFormation — describe stacks + stuck-stack recovery
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'cloudformation:DescribeStacks',          // deploy status checks, SES config resolution
              'cloudformation:CancelUpdateStack',       // stuck-stack cleanup steps
              'cloudformation:ContinueUpdateRollback',  // stuck-stack cleanup steps
            ],
            resources: [
              `arn:aws:cloudformation:*:${this.account}:stack/BATbern-${config.envName}-*/*`,
              `arn:aws:cloudformation:*:${this.account}:stack/CDKToolkit/*`,
            ],
          }),

          // ECR — pre-deploy image existence validation
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['ecr:DescribeImages'],
            resources: [
              `arn:aws:ecr:${this.region}:${this.account}:repository/batbern/${config.envName}/*`,
            ],
          }),

          // RDS — pre-deploy database snapshot backup
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'rds:CreateDBSnapshot',
              'rds:DescribeDBInstances',
            ],
            resources: [
              `arn:aws:rds:${this.region}:${this.account}:db:BATbern-${config.envName}-*`,
              `arn:aws:rds:${this.region}:${this.account}:snapshot:BATbern-${config.envName}-*`,
            ],
          }),

          // Cognito — authenticate test users in post-deploy smoke step
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['cognito-idp:InitiateAuth'],
            resources: [
              `arn:aws:cognito-idp:${this.region}:${this.account}:userpool/*`,
            ],
          }),

        ],
      }),
    });

    // ═══════════════════════════════════════════════════════════
    // CDK DEPLOYMENT POLICY
    //
    // Permissions needed for `cdk deploy` to create and manage all
    // infrastructure resources via CloudFormation + CDK bootstrap roles.
    // These are not directly called by workflow YAML steps.
    // ═══════════════════════════════════════════════════════════

    const cdkDeploymentPolicy = new iam.ManagedPolicy(this, 'CdkDeploymentPolicy', {
      managedPolicyName: `batbern-${config.envName}-github-cdk-deployment`,
      description: 'GitHub Actions CDK — permissions for cdk deploy and asset publishing',
      document: new iam.PolicyDocument({
        statements: [

          // STS — assume CDK bootstrap roles (deploy, cfn-exec, file/image publishing, lookup)
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['sts:AssumeRole', 'sts:TagSession'],
            resources: [
              `arn:aws:iam::${this.account}:role/cdk-hnb659fds-deploy-role-${this.account}-*`,
              `arn:aws:iam::${this.account}:role/cdk-hnb659fds-cfn-exec-role-${this.account}-*`,
              `arn:aws:iam::${this.account}:role/cdk-hnb659fds-file-publishing-role-${this.account}-*`,
              `arn:aws:iam::${this.account}:role/cdk-hnb659fds-image-publishing-role-${this.account}-*`,
              `arn:aws:iam::${this.account}:role/cdk-hnb659fds-lookup-role-${this.account}-*`,
            ],
          }),

          // CloudFormation — full deploy/update lifecycle for CDK stacks
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'cloudformation:CreateStack',
              'cloudformation:UpdateStack',
              'cloudformation:DeleteStack',
              'cloudformation:DescribeStackEvents',
              'cloudformation:DescribeStackResources',
              'cloudformation:GetTemplate',
              'cloudformation:ListStacks',
              'cloudformation:ListStackResources',
              'cloudformation:ListExports',
              'cloudformation:ValidateTemplate',
              'cloudformation:CreateChangeSet',
              'cloudformation:DescribeChangeSet',
              'cloudformation:ExecuteChangeSet',
              'cloudformation:DeleteChangeSet',
              'cloudformation:GetTemplateSummary',
            ],
            resources: [
              `arn:aws:cloudformation:*:${this.account}:stack/BATbern-${config.envName}-*/*`,
              `arn:aws:cloudformation:*:${this.account}:stack/CDKToolkit/*`,
            ],
          }),

          // S3 — CDK asset buckets (multiple regions) + application buckets
          new iam.PolicyStatement({
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
              // CDK asset buckets exist in multiple regions (eu-central-1 + us-east-1 for ACM/CloudFront)
              `arn:aws:s3:::cdk-*-assets-${this.account}-*`,
              `arn:aws:s3:::cdk-*-assets-${this.account}-*/*`,
              `arn:aws:s3:::BATbern-${config.envName}-*`,
              `arn:aws:s3:::BATbern-${config.envName}-*/*`,
              `arn:aws:s3:::batbern-*-${config.envName}`,
              `arn:aws:s3:::batbern-*-${config.envName}/*`,
            ],
          }),

          // ECR — Docker image push/pull for CDK image-publishing role
          // GetAuthorizationToken requires '*'; push/pull scoped to our repositories
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['ecr:GetAuthorizationToken'],
            resources: ['*'],
          }),
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
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
            resources: [
              `arn:aws:ecr:${this.region}:${this.account}:repository/batbern/${config.envName}/*`,
            ],
          }),

          // IAM — create/manage roles and policies for CDK-provisioned resources
          new iam.PolicyStatement({
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
              `arn:aws:iam::${this.account}:role/BATbern-${config.envName}-*`,
              `arn:aws:iam::${this.account}:role/cdk-*`,
              `arn:aws:iam::${this.account}:policy/BATbern-${config.envName}-*`,
            ],
          }),

          // IAM PassRole — pass roles to CloudFormation, Lambda, and ECS during CDK deploy
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: ['iam:PassRole'],
            resources: [
              `arn:aws:iam::${this.account}:role/BATbern-${config.envName}-*`,
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
          }),

          // Lambda — CDK custom resources and Cognito trigger functions
          new iam.PolicyStatement({
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
              `arn:aws:lambda:${this.region}:${this.account}:function:BATbern-${config.envName}-*`,
              `arn:aws:lambda:${this.region}:${this.account}:function:cdk-*`,
            ],
          }),

          // EC2 / VPC — network infrastructure provisioning
          new iam.PolicyStatement({
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
            resources: ['*'], // EC2 VPC operations require '*'
          }),

          // RDS — database infrastructure provisioning
          new iam.PolicyStatement({
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
              'rds:DescribeDBSnapshots',
              'rds:ListTagsForResource',
            ],
            resources: [
              `arn:aws:rds:${this.region}:${this.account}:db:BATbern-${config.envName}-*`,
              `arn:aws:rds:${this.region}:${this.account}:cluster:BATbern-${config.envName}-*`,
              `arn:aws:rds:${this.region}:${this.account}:subgrp:BATbern-${config.envName}-*`,
              `arn:aws:rds:${this.region}:${this.account}:pg:BATbern-${config.envName}-*`,
              `arn:aws:rds:${this.region}:${this.account}:snapshot:BATbern-${config.envName}-*`,
            ],
          }),

          // ElastiCache — Redis infrastructure provisioning
          new iam.PolicyStatement({
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
              `arn:aws:elasticache:${this.region}:${this.account}:cluster:BATbern-${config.envName}-*`,
              `arn:aws:elasticache:${this.region}:${this.account}:replicationgroup:BATbern-${config.envName}-*`,
              `arn:aws:elasticache:${this.region}:${this.account}:subnetgroup:BATbern-${config.envName}-*`,
              `arn:aws:elasticache:${this.region}:${this.account}:parametergroup:BATbern-${config.envName}-*`,
            ],
          }),

          // Cognito — user pool infrastructure provisioning (CDK, not runtime auth)
          new iam.PolicyStatement({
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
          }),

          // CloudFront — CDN provisioning; no resource-level permissions supported
          new iam.PolicyStatement({
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
            resources: ['*'],
          }),

          // Secrets Manager — create/manage secrets and read credentials
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'secretsmanager:GetSecretValue',
              'secretsmanager:DescribeSecret',
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
              // RDS-generated secrets (auto-named by CDK)
              `arn:aws:secretsmanager:${this.region}:${this.account}:secret:RdsClusterInstanceSecret*`,
            ],
          }),

          // KMS — encryption key management
          new iam.PolicyStatement({
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
              `arn:aws:kms:${this.region}:${this.account}:alias/BATbern-${config.envName}-*`,
            ],
          }),

          // SSM Parameter Store — configuration parameters + CDK bootstrap params
          new iam.PolicyStatement({
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
              // CDK bootstrap parameters — required for CDK deployments
              `arn:aws:ssm:*:${this.account}:parameter/cdk-bootstrap/*`,
            ],
          }),

          // CloudWatch + Logs — alarms, dashboards, log groups
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'logs:CreateLogGroup',
              'logs:CreateLogStream',
              'logs:PutLogEvents',
              'logs:DescribeLogGroups',
              'logs:DescribeLogStreams',
              'logs:DeleteLogGroup',
              'logs:PutRetentionPolicy',
              'logs:DeleteRetentionPolicy',
              'logs:TagLogGroup',
              'logs:UntagLogGroup',
              'cloudwatch:PutMetricAlarm',
              'cloudwatch:DeleteAlarms',
              'cloudwatch:DescribeAlarms',
              'cloudwatch:PutDashboard',
              'cloudwatch:GetDashboard',
              'cloudwatch:DeleteDashboards',
              'cloudwatch:ListDashboards',
            ],
            resources: [
              `arn:aws:logs:${this.region}:${this.account}:log-group:*`,
              `arn:aws:cloudwatch:${this.region}:${this.account}:alarm:BATbern-${config.envName}-*`,
            ],
          }),

          // ACM — SSL/TLS certificates; no resource-level permissions for some actions
          new iam.PolicyStatement({
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
            resources: ['*'],
          }),

          // Route53 — DNS record management
          new iam.PolicyStatement({
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
              'arn:aws:route53:::hostedzone/*',
              'arn:aws:route53:::change/*',
            ],
          }),

          // ECS — cluster and service lifecycle management (provisioning, not runtime)
          new iam.PolicyStatement({
            effect: iam.Effect.ALLOW,
            actions: [
              'ecs:CreateCluster',
              'ecs:DeleteCluster',
              'ecs:CreateService',
              'ecs:DeleteService',
              'ecs:DeregisterTaskDefinition',
              'ecs:DescribeTasks',
              'ecs:ListTasks',
              'ecs:TagResource',
              'ecs:UntagResource',
            ],
            resources: ['*'],
          }),

          // Application Auto Scaling — ECS service scaling policies
          new iam.PolicyStatement({
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
          }),

        ],
      }),
    });

    githubActionsRole.addManagedPolicy(workflowRuntimePolicy);
    githubActionsRole.addManagedPolicy(cdkDeploymentPolicy);

    this.githubActionsRole = githubActionsRole;

    // ═══════════════════════════════════════════════════════════
    // CLOUDWATCH LOG GROUPS
    // ═══════════════════════════════════════════════════════════

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
