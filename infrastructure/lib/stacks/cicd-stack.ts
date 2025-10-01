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
      'company-management-service',
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

    this.githubActionsRole = githubActionsRole;

    // ═══════════════════════════════════════════════════════════
    // CLOUDWATCH LOG GROUPS
    // ═══════════════════════════════════════════════════════════

    // Log group for CI/CD pipeline logs
    const pipelineLogGroup = new cdk.aws_logs.LogGroup(this, 'PipelineLogGroup', {
      logGroupName: `/batbern/cicd/${config.envName}/pipeline`,
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
