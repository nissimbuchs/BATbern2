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
  /**
   * Whether alarm issues carry the '@claude' triage handoff that invokes
   * .github/workflows/claude.yml (#986). Defaults to true.
   *
   * Exposed as a Lambda environment variable rather than baked in, because a Lambda env var
   * change is effective immediately: if a flapping alarm starts one agent run per cycle, this
   * can be flipped in the console without deploying code. Same reasoning as
   * FEATURES_SSO_ENABLED.
   */
  claudeTriageEnabled?: boolean;
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

    // Defaults match the real repository. They previously read 'batbern' /
    // 'BATbern-develop' — neither of which exists ('BATbern-develop' is a local checkout
    // directory name, not a repo) — so any caller relying on the defaults would have
    // silently pointed the Lambda at a 404 and filed nothing. `bin/batbern-infrastructure.ts`
    // always passes real values, which is why this never bit; it stays a trap regardless.
    const githubOwner = props.githubOwner || 'nissimbuchs';
    const githubRepo = props.githubRepo || 'BATbern2';
    const githubTokenParam =
      props.githubTokenParamName || `/batbern/${props.environment}/github/token`;

    // Lambda function for GitHub Issues integration
    this.lambdaFunction = new nodejs.NodejsFunction(this, 'GitHubIssuesFunction', {
      functionName: `batbern-${props.environment}-github-issues`,
      entry: path.join(__dirname, '../../lambda/github-issues-integration/index.ts'),
      handler: 'handler',
      runtime: lambda.Runtime.NODEJS_24_X,
      timeout: cdk.Duration.seconds(30),
      memorySize: 256,
      environment: {
        GITHUB_OWNER: githubOwner,
        GITHUB_REPO: githubRepo,
        GITHUB_TOKEN_PARAM: githubTokenParam,
        CLAUDE_TRIAGE_ENABLED: String(props.claudeTriageEnabled ?? true),
      },
      bundling: {
        externalModules: ['@aws-sdk/*'], // AWS SDK v3 is provided by Lambda runtime
        minify: true,
        sourceMap: true,
        forceDockerBundling: false, // Prefer local esbuild over Docker for faster builds on ARM64
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

    // #996: read-only CloudWatch access so the Lambda can embed the alarm's own recent
    // datapoints in the issue body. Read verbs only, enumerated rather than 'cloudwatch:*' —
    // that would include DeleteAlarms, PutMetricAlarm and SetAlarmState, none of which a
    // notification formatter has any business holding. Resource is * because GetMetricData is
    // not resource-scopable and DescribeAlarms is queried by name at call time.
    this.lambdaFunction.addToRolePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['cloudwatch:DescribeAlarms', 'cloudwatch:GetMetricData'],
        resources: ['*'],
      })
    );

    // #1002: read the service log groups so the issue body can carry an AGGREGATED, REDACTED
    // picture of what was happening — see fetchLogContext/redactPath in the handler. FilterLogEvents
    // only; no logs:PutLogEvents to these groups, no DeleteLogGroup, and scoped to the
    // /aws/ecs/BATbern-* prefix rather than '*' so it cannot read Cognito trigger or
    // email-forwarder logs it has no business in.
    this.lambdaFunction.addToRolePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['logs:FilterLogEvents'],
        resources: [
          `arn:aws:logs:${cdk.Stack.of(this).region}:${cdk.Stack.of(this).account}:log-group:/aws/ecs/BATbern-*`,
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
