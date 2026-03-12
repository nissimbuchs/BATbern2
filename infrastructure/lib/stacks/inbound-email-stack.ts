import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as sesActions from 'aws-cdk-lib/aws-ses-actions';
import * as s3n from 'aws-cdk-lib/aws-s3-notifications';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as route53 from 'aws-cdk-lib/aws-route53';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import * as path from 'path';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface InboundEmailStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  emsTaskRole?: iam.IRole;
  /** Route53 hosted zone for the inbound domain (used for SES identity DNS verification). */
  hostedZone?: route53.IHostedZone;
  /** VPC for Lambda to access services via Service Connect DNS (Story 10.26). */
  vpc?: ec2.IVpc;
  /** Security group allowing Lambda to communicate with ECS services (Story 10.26). */
  lambdaSecurityGroup?: ec2.ISecurityGroup;
  /**
   * Public API Gateway base URL (e.g. https://api.batbern.ch).
   * When provided, the forwarder Lambda uses this instead of the ECS Service Connect DNS,
   * which is not resolvable from VPC-native Lambda functions.
   */
  apiGatewayPublicUrl?: string;
}

/**
 * InboundEmailStack — SES inbound email pipeline (Story 10.17 + 10.26)
 *
 * Data flow (Story 10.17 — reply routing):
 *   replies@{inboundDomain} → SES receipt rule → S3 bucket (emails/) → S3 event notification
 *   → SQS queue → @SqsListener in EMS → MIME parse → route (unsubscribe/cancel)
 *
 * Data flow (Story 10.26 — email forwarding):
 *   ok@/info@/events@/partner@/support@/batbern{N}@batbern.ch → SES receipt rule
 *   → S3 bucket (forwarding/) → S3 event notification → Forwarder Lambda
 *   → resolve recipients via API → re-send via SES SendRawEmail
 *
 * Deployed to eu-central-1 (same region as all other stacks). SES inbound email receiving
 * expanded to eu-central-1 in September 2023 — no cross-region deployment required.
 *
 * Environment isolation: each environment uses a distinct reply address so replies
 * are routed to the correct SES account:
 *   production  → replies@batbern.ch         (batbern.ch MX → prod account SES)
 *   staging     → replies@staging.batbern.ch  (staging.batbern.ch MX → staging account SES)
 */
export class InboundEmailStack extends cdk.Stack {
  public readonly inboundQueue: sqs.IQueue;
  public readonly inboundBucket: s3.IBucket;
  public readonly replyAddress: string;

  constructor(scope: Construct, id: string, props: InboundEmailStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Environment-specific reply-to address for environment isolation.
    // Each environment's SES account only receives replies addressed to its own subdomain,
    // preventing staging replies from landing in the production service or vice versa.
    const isProdTraffic = props.config.isProduction ?? (envName === 'production');
    const replyDomain = isProdTraffic
      ? 'batbern.ch'
      : `${envName}.batbern.ch`; // e.g. staging.batbern.ch
    const forwardingDomain = isProdTraffic ? 'batbern.ch' : `${envName}.batbern.ch`;
    const replyAddress = `replies@${replyDomain}`;

    // Expose for use in bin file (EMAIL_REPLY_TO env var on EMS)
    this.replyAddress = replyAddress;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'InboundEmail');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Dead-letter queue for failed inbound email processing
    const dlq = new sqs.Queue(this, 'InboundEmailDLQ', {
      queueName: `batbern-inbound-email-dlq-${envName}`,
      retentionPeriod: cdk.Duration.days(14),
    });

    // Standard SQS queue (not FIFO — handlers are idempotent)
    const inboundQueue = new sqs.Queue(this, 'InboundEmailQueue', {
      queueName: `batbern-inbound-email-${envName}`,
      visibilityTimeout: cdk.Duration.seconds(300),
      retentionPeriod: cdk.Duration.days(1),
      deadLetterQueue: {
        queue: dlq,
        maxReceiveCount: 3,
      },
    });
    this.inboundQueue = inboundQueue;

    // Grant SES permission to send messages to SQS
    inboundQueue.addToResourcePolicy(
      new iam.PolicyStatement({
        principals: [new iam.ServicePrincipal('ses.amazonaws.com')],
        actions: ['sqs:SendMessage'],
        resources: [inboundQueue.queueArn],
        conditions: {
          StringEquals: { 'aws:SourceAccount': this.account },
        },
      }),
    );

    // S3 bucket for raw inbound emails (7-day auto-purge)
    const inboundBucket = new s3.Bucket(this, 'InboundEmailBucket', {
      bucketName: `batbern-inbound-emails-${envName}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      lifecycleRules: [
        {
          expiration: cdk.Duration.days(7),
          enabled: true,
        },
      ],
    });
    this.inboundBucket = inboundBucket;

    // Grant SES permission to write raw emails to S3
    inboundBucket.addToResourcePolicy(
      new iam.PolicyStatement({
        principals: [new iam.ServicePrincipal('ses.amazonaws.com')],
        actions: ['s3:PutObject'],
        resources: [`${inboundBucket.bucketArn}/*`],
        conditions: {
          StringEquals: { 'aws:Referer': this.account },
        },
      }),
    );

    // S3 event notification → SQS for reply emails (emails/ prefix)
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(inboundQueue),
      { prefix: 'emails/' },
    );

    // SES receipt rule set for inbound email
    const ruleSet = new ses.ReceiptRuleSet(this, 'InboundRuleSet', {
      receiptRuleSetName: `batbern-inbound-${envName}`,
    });

    // Rule 1 (highest priority): Route replies to S3 emails/ prefix → SQS (Story 10.17)
    // StopAction prevents fall-through to the catch-all domain rule (Rule 3).
    ruleSet.addRule('RouteReplies', {
      recipients: [replyAddress],
      actions: [
        new sesActions.S3({
          bucket: inboundBucket,
          objectKeyPrefix: 'emails/',
        }),
        new sesActions.Stop(),
      ],
      enabled: true,
    });

    // Rule 2: Route named forwarding addresses to S3 forwarding/ prefix (Story 10.26)
    // StopAction prevents fall-through to the catch-all domain rule (Rule 3), which would
    // otherwise also match these addresses and trigger the Lambda a second time.
    const forwardingRule = ruleSet.addRule('RouteForwardingNamed', {
      recipients: [
        `ok@${forwardingDomain}`,
        `info@${forwardingDomain}`,
        `events@${forwardingDomain}`,
        `partner@${forwardingDomain}`,
        `support@${forwardingDomain}`,
      ],
      actions: [
        new sesActions.S3({
          bucket: inboundBucket,
          objectKeyPrefix: 'forwarding/',
        }),
        new sesActions.Stop(),
      ],
      enabled: true,
    });

    // Rule 3 (lowest priority): Catch-all domain rule for batbern{N}@ addresses (Story 10.26)
    const catchAllRule = ruleSet.addRule('RouteForwardingCatchAll', {
      recipients: [forwardingDomain],
      actions: [
        new sesActions.S3({
          bucket: inboundBucket,
          objectKeyPrefix: 'forwarding/',
        }),
      ],
      enabled: true,
    });

    // Ensure ordering: replies first, named forwarding second, catch-all third
    forwardingRule.node.addDependency(ruleSet.node.findChild('RouteReplies'));
    catchAllRule.node.addDependency(forwardingRule);

    // ========================
    // Email Forwarder Lambda (Story 10.26)
    // ========================

    const forwarderLogGroup = new logs.LogGroup(this, 'ForwarderLogGroup', {
      logGroupName: `/aws/lambda/batbern-email-forwarder-${envName}`,
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // API Gateway URL for the forwarder to call existing APIs.
    // ECS Service Connect DNS (api-gateway.batbern-{env}) is only resolvable from within ECS
    // tasks — VPC-native Lambda functions use the standard VPC DNS which cannot resolve it.
    // Use the public API domain when available; fall back to Service Connect for local/dev.
    const apiGatewayUrl = props.apiGatewayPublicUrl
      ? props.apiGatewayPublicUrl
      : `http://api-gateway.batbern-${envName}:8080`;

    const forwarderLambda = new NodejsFunction(this, 'EmailForwarder', {
      functionName: `batbern-email-forwarder-${envName}`,
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '../../lambda/email-forwarder/index.ts'),
      handler: 'handler',
      memorySize: 256,
      timeout: cdk.Duration.seconds(60),
      logGroup: forwarderLogGroup,
      // VPC access: Lambda needs to call services via Service Connect DNS
      ...(props.vpc && props.lambdaSecurityGroup ? {
        vpc: props.vpc,
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        securityGroups: [props.lambdaSecurityGroup],
      } : {}),
      environment: {
        API_GATEWAY_URL: apiGatewayUrl,
        SES_SENDER_ADDRESS: `noreply@${forwardingDomain}`,
        FORWARDING_DOMAIN: forwardingDomain,
      },
      bundling: {
        externalModules: ['@aws-sdk/*'],
        minify: true,
        sourceMap: false,
        forceDockerBundling: false,
      },
    });

    // Grant Lambda S3 read access for fetching raw emails
    inboundBucket.grantRead(forwarderLambda);

    // Grant Lambda SES send permissions
    forwarderLambda.addToRolePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['ses:SendEmail', 'ses:SendRawEmail'],
        resources: ['*'],
      }),
    );

    // Grant Lambda CloudWatch PutMetricData permission
    forwarderLambda.addToRolePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
        conditions: {
          StringEquals: { 'cloudwatch:namespace': 'BATbern/EmailForwarder' },
        },
      }),
    );

    // S3 event notification → Lambda for forwarding emails (forwarding/ prefix)
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.LambdaDestination(forwarderLambda),
      { prefix: 'forwarding/' },
    );

    // CloudWatch alarm for abuse detection (AC10)
    new cloudwatch.Alarm(this, 'EmailsRejectedAlarm', {
      alarmName: `batbern-${envName}-emails-rejected`,
      alarmDescription: 'Email forwarding: high rejection rate may indicate abuse',
      metric: new cloudwatch.Metric({
        namespace: 'BATbern/EmailForwarder',
        metricName: 'EmailsRejected',
        statistic: 'Sum',
        period: cdk.Duration.hours(1),
      }),
      threshold: 20,
      evaluationPeriods: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // SES domain identity for the inbound domain — required for SES to accept inbound mail.
    // Without a verified identity for replyDomain, SES rejects with 550 5.1.1 mailbox unavailable.
    // If a hosted zone is provided, CDK auto-adds the DKIM CNAME and TXT verification records.
    if (props.hostedZone) {
      new ses.EmailIdentity(this, 'InboundDomainIdentity', {
        identity: ses.Identity.publicHostedZone(props.hostedZone),
      });

      // Story 10.26: MX record pointing batbern.ch to SES inbound SMTP endpoint.
      // Required for the world's mail servers to deliver email to @batbern.ch → SES.
      new route53.MxRecord(this, 'InboundMxRecord', {
        zone: props.hostedZone,
        values: [{ priority: 10, hostName: `inbound-smtp.${props.config.region}.amazonaws.com` }],
        ttl: cdk.Duration.hours(1),
        comment: 'SES inbound email for forwarding distribution lists (Story 10.26)',
      });
    }

    // Grant EMS task role permissions to consume messages and read email objects
    if (props.emsTaskRole) {
      inboundQueue.grantConsumeMessages(props.emsTaskRole);
      inboundBucket.grantRead(props.emsTaskRole);
    }

    // CloudFormation outputs for EMS env var injection
    new cdk.CfnOutput(this, 'InboundQueueUrl', {
      value: inboundQueue.queueUrl,
      exportName: `${id}-InboundQueueUrl`,
    });
    new cdk.CfnOutput(this, 'InboundBucketName', {
      value: inboundBucket.bucketName,
      exportName: `${id}-InboundBucketName`,
    });
  }
}
