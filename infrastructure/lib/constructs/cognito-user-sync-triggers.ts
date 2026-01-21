import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as events from 'aws-cdk-lib/aws-events';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import { Construct } from 'constructs';
import * as path from 'path';

export interface CognitoUserSyncTriggersProps {
  userPool: cognito.UserPool; // Must be concrete type for addTrigger() method
  vpc: ec2.IVpc;
  lambdaSecurityGroup: ec2.ISecurityGroup; // Pre-created in NetworkStack to avoid cyclic dependency
  databaseSecret: secretsmanager.ISecret;
  databaseEndpoint: string;
  envName: string;
  eventBus?: events.IEventBus; // Story 6.3: EventBridge for SpeakerPoolLinked events
  eventManagementServiceUrl?: string; // Story 6.3: URL to call ensure speaker endpoint
}

/**
 * Cognito User Sync Triggers Construct
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * Creates Lambda triggers for PostConfirmation, PreTokenGeneration, PreAuthentication, and PostAuthentication
 * <p>
 * Triggers:
 * - PostConfirmation: Creates database user after Cognito email verification
 * - PreTokenGeneration: Enriches JWT with roles from database
 * - PreAuthentication: Blocks inactive users from authenticating
 * - PostAuthentication: Links anonymous user profiles to Cognito accounts (ADR-005)
 */
export class CognitoUserSyncTriggers extends Construct {
  public readonly postConfirmationTrigger: lambda.Function;
  public readonly preTokenGenerationTrigger: lambda.Function;
  public readonly preAuthenticationTrigger: lambda.Function;
  public readonly postAuthenticationTrigger: lambda.Function;

  constructor(scope: Construct, id: string, props: CognitoUserSyncTriggersProps) {
    super(scope, id);

    const isProd = props.envName === 'production';

    // Common Lambda environment variables
    // Secrets are read dynamically at runtime, not at CDK synth time
    // Note: AWS_REGION is automatically provided by Lambda runtime and cannot be set manually
    const commonEnv: { [key: string]: string } = {
      DB_HOST: props.databaseEndpoint,
      DB_NAME: 'batbern',
      DB_SECRET_ARN: props.databaseSecret.secretArn,
      LOG_LEVEL: isProd ? 'INFO' : 'DEBUG',
    };

    // Story 6.3: Add EventBus name for SpeakerPoolLinked event publishing
    if (props.eventBus) {
      commonEnv.EVENT_BUS_NAME = props.eventBus.eventBusName;
    }

    // Story 6.3: Add Event Management Service URL for Speaker entity creation
    if (props.eventManagementServiceUrl) {
      commonEnv.EVENT_MANAGEMENT_SERVICE_URL = props.eventManagementServiceUrl;
    }

    // Common Lambda props
    const commonLambdaProps = {
      runtime: lambda.Runtime.NODEJS_20_X,
      memorySize: 512,
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [props.lambdaSecurityGroup], // Use pre-created SG from NetworkStack
      environment: commonEnv,
      bundling: {
        externalModules: ['@aws-sdk/*'], // Use AWS SDK from Lambda runtime
        minify: true,
        sourceMap: false,
        forceDockerBundling: false, // Prefer local esbuild over Docker for faster builds on ARM64
      },
    };

    // PostConfirmation Lambda Trigger
    const postConfirmationLogGroup = new logs.LogGroup(this, 'PostConfirmationLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${props.envName}/post-confirmation-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.postConfirmationTrigger = new NodejsFunction(this, 'PostConfirmationTrigger', {
      ...commonLambdaProps,
      functionName: `batbern-${props.envName}-post-confirmation-trigger`,
      entry: path.join(__dirname, '../lambda/triggers/post-confirmation.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(10),
      logGroup: postConfirmationLogGroup,
    });

    // PreTokenGeneration Lambda Trigger
    const preTokenGenerationLogGroup = new logs.LogGroup(this, 'PreTokenGenerationLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${props.envName}/pre-token-generation-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.preTokenGenerationTrigger = new NodejsFunction(this, 'PreTokenGenerationTrigger', {
      ...commonLambdaProps,
      functionName: `batbern-${props.envName}-pre-token-generation-trigger`,
      entry: path.join(__dirname, '../lambda/triggers/pre-token-generation.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(15), // Increased from 5s for VPC cold starts + DB query
      logGroup: preTokenGenerationLogGroup,
    });

    // PreAuthentication Lambda Trigger
    const preAuthenticationLogGroup = new logs.LogGroup(this, 'PreAuthenticationLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${props.envName}/pre-authentication-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.preAuthenticationTrigger = new NodejsFunction(this, 'PreAuthenticationTrigger', {
      ...commonLambdaProps,
      functionName: `batbern-${props.envName}-pre-authentication-trigger`,
      entry: path.join(__dirname, '../lambda/triggers/pre-authentication.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(15), // Increased from 5s for VPC cold starts + DB query
      logGroup: preAuthenticationLogGroup,
    });

    // PostAuthentication Lambda Trigger (ADR-005: Anonymous user account linking)
    const postAuthenticationLogGroup = new logs.LogGroup(this, 'PostAuthenticationLogGroup', {
      logGroupName: `/aws/lambda/BATbern-${props.envName}/post-authentication-trigger`,
      retention: isProd ? logs.RetentionDays.ONE_MONTH : logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.postAuthenticationTrigger = new NodejsFunction(this, 'PostAuthenticationTrigger', {
      ...commonLambdaProps,
      functionName: `batbern-${props.envName}-post-authentication-trigger`,
      entry: path.join(__dirname, '../lambda/triggers/post-authentication.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(10),
      logGroup: postAuthenticationLogGroup,
    });

    // Grant secret read permissions
    props.databaseSecret.grantRead(this.postConfirmationTrigger);
    props.databaseSecret.grantRead(this.preTokenGenerationTrigger);
    props.databaseSecret.grantRead(this.preAuthenticationTrigger);
    props.databaseSecret.grantRead(this.postAuthenticationTrigger);

    // Grant CloudWatch permissions
    const cloudWatchPolicy = new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['cloudwatch:PutMetricData'],
      resources: ['*'],
    });

    this.postConfirmationTrigger.addToRolePolicy(cloudWatchPolicy);
    this.preTokenGenerationTrigger.addToRolePolicy(cloudWatchPolicy);
    this.preAuthenticationTrigger.addToRolePolicy(cloudWatchPolicy);
    this.postAuthenticationTrigger.addToRolePolicy(cloudWatchPolicy);

    // Story 6.3: Grant EventBridge publish permission to PostConfirmation trigger
    // This allows the Lambda to publish SpeakerPoolLinked events after linking
    if (props.eventBus) {
      props.eventBus.grantPutEventsTo(this.postConfirmationTrigger);
    }

    // Note: Database security group ingress rule is configured in VpcConstruct
    // to avoid cyclic dependency (Network -> CompanyManagement -> Network)

    // Grant Cognito invoke permissions
    props.userPool.addTrigger(
      cognito.UserPoolOperation.POST_CONFIRMATION,
      this.postConfirmationTrigger
    );
    props.userPool.addTrigger(
      cognito.UserPoolOperation.PRE_TOKEN_GENERATION,
      this.preTokenGenerationTrigger
    );
    props.userPool.addTrigger(
      cognito.UserPoolOperation.PRE_AUTHENTICATION,
      this.preAuthenticationTrigger
    );
    props.userPool.addTrigger(
      cognito.UserPoolOperation.POST_AUTHENTICATION,
      this.postAuthenticationTrigger
    );
  }
}
