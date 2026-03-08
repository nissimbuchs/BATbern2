import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as sesActions from 'aws-cdk-lib/aws-ses-actions';
import * as s3n from 'aws-cdk-lib/aws-s3-notifications';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface InboundEmailStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  emsTaskRole?: iam.IRole;
}

/**
 * InboundEmailStack — SES inbound email pipeline (Story 10.17)
 *
 * Data flow:
 *   replies@batbern.ch → SES receipt rule → S3 bucket → S3 event notification
 *   → SQS queue → @SqsListener in EMS → MIME parse → route (unsubscribe/cancel)
 *
 * IMPORTANT: This stack MUST be deployed to eu-west-1 because AWS SES inbound email
 * receiving is only supported in us-east-1, us-west-2, and eu-west-1.
 * BATbern's primary region (eu-central-1) does NOT support SES inbound.
 */
export class InboundEmailStack extends cdk.Stack {
  public readonly inboundQueue: sqs.IQueue;
  public readonly inboundBucket: s3.IBucket;

  constructor(scope: Construct, id: string, props: InboundEmailStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

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

    // S3 event notification → SQS (no SNS intermediary needed)
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(inboundQueue),
    );

    // SES receipt rule set for inbound email
    const ruleSet = new ses.ReceiptRuleSet(this, 'InboundRuleSet', {
      receiptRuleSetName: `batbern-inbound-${envName}`,
    });

    ruleSet.addRule('RouteReplies', {
      recipients: ['replies@batbern.ch'],
      actions: [
        new sesActions.S3({
          bucket: inboundBucket,
          objectKeyPrefix: 'emails/',
        }),
      ],
      enabled: true,
    });

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
