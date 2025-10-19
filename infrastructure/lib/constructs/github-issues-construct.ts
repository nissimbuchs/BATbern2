import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as nodejs from 'aws-cdk-lib/aws-lambda-nodejs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import * as path from 'path';

export interface GitHubIssuesConstructProps {
  alarmTopic: sns.Topic;
  environment: string;
  githubOwner?: string;
  githubRepo?: string;
  githubTokenParamName?: string;
}

/**
 * Construct for GitHub Issues integration with CloudWatch alarms.
 *
 * Automatically creates GitHub Issues when alarms trigger and closes them when resolved.
 */
export class GitHubIssuesConstruct extends Construct {
  public readonly lambdaFunction: lambda.Function;

  constructor(scope: Construct, id: string, props: GitHubIssuesConstructProps) {
    super(scope, id);

    const githubOwner = props.githubOwner || 'batbern';
    const githubRepo = props.githubRepo || 'BATbern-develop';
    const githubTokenParam = props.githubTokenParamName || `/batbern/${props.environment}/github/token`;

    // Lambda function for GitHub Issues integration
    this.lambdaFunction = new nodejs.NodejsFunction(this, 'GitHubIssuesFunction', {
      functionName: `batbern-${props.environment}-github-issues`,
      entry: path.join(__dirname, '../../lambda/github-issues-integration/index.ts'),
      handler: 'handler',
      runtime: lambda.Runtime.NODEJS_20_X,
      timeout: cdk.Duration.seconds(30),
      memorySize: 256,
      environment: {
        GITHUB_OWNER: githubOwner,
        GITHUB_REPO: githubRepo,
        GITHUB_TOKEN_PARAM: githubTokenParam,
      },
      bundling: {
        externalModules: ['@aws-sdk/*'], // AWS SDK v3 is provided by Lambda runtime
        minify: true,
        sourceMap: true,
      },
    });

    // Grant permission to read GitHub token from SSM Parameter Store
    this.lambdaFunction.addToRolePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['ssm:GetParameter'],
        resources: [
          `arn:aws:ssm:${cdk.Stack.of(this).region}:${cdk.Stack.of(this).account}:parameter${githubTokenParam}`,
        ],
      })
    );

    // Subscribe Lambda to SNS alarm topic
    props.alarmTopic.addSubscription(new subscriptions.LambdaSubscription(this.lambdaFunction));

    // Output Lambda function ARN
    new cdk.CfnOutput(this, 'GitHubIssuesFunctionArn', {
      value: this.lambdaFunction.functionArn,
      description: 'GitHub Issues integration Lambda function ARN',
      exportName: `${props.environment}-GitHubIssuesFunctionArn`,
    });
  }
}
