import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as snsSubscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as sesActions from 'aws-cdk-lib/aws-ses-actions';
import * as s3n from 'aws-cdk-lib/aws-s3-notifications';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as cloudwatchActions from 'aws-cdk-lib/aws-cloudwatch-actions';
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

  /**
   * SNS topic for alarm + recovery notifications.
   *
   * #1001: without this, EmailsRejectedAlarm below was created with NO AlarmActions and NO
   * OKActions — verified live, both arrays empty. It could fire and nothing would happen: no
   * mail, no GitHub issue, no record. A declared alarm is not a working alarm (#970), and this
   * stack was missed by the #977 sweep because it defines its alarm locally rather than through
   * MonitoringStack.
   */
  alarmTopic?: sns.ITopic;
}

/**
 * InboundEmailStack — SES inbound email pipeline (Story 10.17 + 10.26)
 *
 * Data flow (Story 10.17 — reply routing):
 *   replies@{inboundDomain} → SES receipt rule → S3 bucket (emails/) → S3 event notification
 *   → SQS queue → @SqsListener in EMS → MIME parse → route (unsubscribe/cancel)
 *
 * Data flow (Story 10.26 — email forwarding, moved into EMS 2026-10):
 *   ok@/info@/events@/partner@/support@/batbern{N}[-speaker|-moderator|-participants]@batbern.ch
 *   → SES receipt rule → S3 bucket (forwarding/) → S3 event notification → SQS forwarding queue
 *   → @SqsListener in EMS (EmailForwardingListener) → sender check + recipients resolved
 *   in-process → re-send via SES SendRawEmail
 *
 *   This replaced a Lambda that resolved recipients by calling the public API anonymously,
 *   which required opening those endpoints to the internet. Access is now IAM only: SES writes
 *   to S3, S3 notifies SQS, the EMS task role consumes. No HTTP endpoint is involved.
 *
 * Deployed to eu-central-1 (same region as all other stacks). SES inbound email receiving
 * expanded to eu-central-1 in September 2023 — no cross-region deployment required.
 *
 * Environment isolation: each environment uses a distinct reply address so replies
 * are routed to the correct SES account:
 *   production  → replies@batbern.ch         (batbern.ch MX → prod account SES)
 *   other envs  → replies@<env>.batbern.ch. Not deployed: production traffic runs on the
 *                 staging stacks with isProdTraffic, and staging.batbern.ch has no MX (#1133).
 */
export class InboundEmailStack extends cdk.Stack {
  public readonly inboundQueue: sqs.IQueue;
  public readonly inboundBucket: s3.IBucket;
  public readonly replyAddress: string;
  /** SQS queue EMS consumes for alias forwarding (S3 forwarding/ prefix). */
  public readonly forwardingQueue: sqs.IQueue;
  /** Domain whose aliases are forwarded, e.g. batbern.ch. */
  public readonly forwardingDomain: string;
  /** SES configuration set EMS attaches to forwarded mail (delivery tracking). */
  public readonly forwarderConfigurationSetName: string;

  constructor(scope: Construct, id: string, props: InboundEmailStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Environment-specific reply-to address for environment isolation.
    // Each environment's SES account only receives replies addressed to its own subdomain,
    // preventing staging replies from landing in the production service or vice versa.
    const isProdTraffic = props.config.isProduction ?? envName === 'production';
    const replyDomain = isProdTraffic ? 'batbern.ch' : `${envName}.batbern.ch`; // e.g. staging.batbern.ch
    const forwardingDomain = isProdTraffic ? 'batbern.ch' : `${envName}.batbern.ch`;
    const replyAddress = `replies@${replyDomain}`;

    // Expose for use in bin file (EMAIL_REPLY_TO env var on EMS)
    this.replyAddress = replyAddress;
    this.forwardingDomain = forwardingDomain;

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

    // Alias forwarding queue (forwarding/ prefix → EMS EmailForwardingListener). Separate from the
    // replies queue: different consumer, different failure handling, and the reply router must
    // never see alias mail. EMS rethrows on failures before the first send, so SQS retries three
    // times and then parks the message here for inspection/replay.
    const forwardingDlq = new sqs.Queue(this, 'ForwardingEmailDLQ', {
      queueName: `batbern-forwarding-email-dlq-${envName}`,
      retentionPeriod: cdk.Duration.days(14),
    });
    const forwardingQueue = new sqs.Queue(this, 'ForwardingEmailQueue', {
      queueName: `batbern-forwarding-email-${envName}`,
      // A 160-recipient participants@ mail at ~14 sends/s takes ~12 s; 300 s leaves headroom.
      // Coupled to EmailForwardingLogService.STALE_CLAIM_SECONDS (360) in EMS, which must stay
      // above this value: raise both together.
      visibilityTimeout: cdk.Duration.seconds(300),
      // Messages survive an EMS outage for 4 days (raw mail itself stays in S3 for 7).
      retentionPeriod: cdk.Duration.days(4),
      deadLetterQueue: {
        queue: forwardingDlq,
        maxReceiveCount: 3,
      },
    });
    this.forwardingQueue = forwardingQueue;

    // Grant SES permission to send messages to SQS
    inboundQueue.addToResourcePolicy(
      new iam.PolicyStatement({
        principals: [new iam.ServicePrincipal('ses.amazonaws.com')],
        actions: ['sqs:SendMessage'],
        resources: [inboundQueue.queueArn],
        conditions: {
          StringEquals: { 'aws:SourceAccount': this.account },
        },
      })
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
      })
    );

    // S3 event notification → SQS for reply emails (emails/ prefix)
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(inboundQueue),
      { prefix: 'emails/' }
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

    // The forwarder Lambda (Story 10.26) was removed when forwarding moved into EMS. Its log
    // group stays so CloudFormation does not delete the last 30 days of forwarder logs with it;
    // they expire on their own (retention below). Safe to delete from the stack after 2026-12-01.
    new logs.LogGroup(this, 'ForwarderLogGroup', {
      logGroupName: `/aws/lambda/batbern-email-forwarder-${envName}`,
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // ========================
    // Delivery tracking (Option A) — dedicated forwarder SES configuration set
    // ========================
    // A standalone config set (NOT the newsletter one) so forwarded speaker/
    // broadcast mail emits delivery/bounce/complaint/reject events to CloudWatch
    // metrics, isolated from the newsletter bounce-SQS + BounceProcessingService
    // (a speaker bounce must never auto-suppress a newsletter recipient). EMS
    // attaches this set to every forwarded mail (AWS_INBOUND_EMAIL_FORWARDING_CONFIGURATION_SET).
    const forwarderConfigSetName = `batbern-${envName}-forwarder`;
    this.forwarderConfigurationSetName = forwarderConfigSetName;
    const forwarderConfigSet = new ses.CfnConfigurationSet(this, 'ForwarderConfigSet', {
      name: forwarderConfigSetName,
    });

    new ses.CfnConfigurationSetEventDestination(this, 'ForwarderEventDest', {
      configurationSetName: forwarderConfigSet.ref,
      eventDestination: {
        name: 'forwarder-delivery-events',
        enabled: true,
        matchingEventTypes: ['delivery', 'bounce', 'complaint', 'reject'],
        cloudWatchDestination: {
          dimensionConfigurations: [
            {
              // Dimension sourced from the message tag SES sets per config set;
              // defaultDimensionValue keeps metrics populated even without tags.
              dimensionName: 'ses:configuration-set',
              dimensionValueSource: 'messageTag',
              defaultDimensionValue: forwarderConfigSetName,
            },
          ],
        },
      },
    });

    // Per-recipient detail: SES events → SNS → small logger Lambda → its own
    // CloudWatch log group, so an operator can answer "did <address> deliver /
    // bounce?" by querying the log group by recipient. This is the per-recipient
    // complement to the aggregate CloudWatch metrics above. A dedicated topic +
    // log group keeps it decoupled from the newsletter bounce SQS pipeline.
    const forwarderEventsTopic = new sns.Topic(this, 'ForwarderEventsTopic', {
      topicName: `batbern-${envName}-ses-forwarder-events`,
    });

    new ses.CfnConfigurationSetEventDestination(this, 'ForwarderSnsEventDest', {
      configurationSetName: forwarderConfigSet.ref,
      eventDestination: {
        name: 'forwarder-per-recipient-events',
        enabled: true,
        matchingEventTypes: ['delivery', 'bounce', 'complaint', 'reject'],
        snsDestination: { topicArn: forwarderEventsTopic.topicArn },
      },
    });

    const sesEventLoggerLogGroup = new logs.LogGroup(this, 'SesEventLoggerLogGroup', {
      logGroupName: `/aws/lambda/batbern-${envName}-ses-event-logger`,
      retention: logs.RetentionDays.THREE_MONTHS,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const sesEventLogger = new NodejsFunction(this, 'SesEventLogger', {
      functionName: `batbern-${envName}-ses-event-logger`,
      runtime: lambda.Runtime.NODEJS_24_X,
      entry: path.join(__dirname, '../../lambda/ses-event-logger/index.ts'),
      handler: 'handler',
      memorySize: 128,
      timeout: cdk.Duration.seconds(30),
      logGroup: sesEventLoggerLogGroup,
      bundling: {
        externalModules: ['@aws-sdk/*'],
        minify: true,
        sourceMap: false,
        forceDockerBundling: false,
      },
    });

    forwarderEventsTopic.addSubscription(new snsSubscriptions.LambdaSubscription(sesEventLogger));

    // S3 event notification → SQS for alias forwarding (forwarding/ prefix) → EMS
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(forwardingQueue),
      { prefix: 'forwarding/' }
    );

    // CloudWatch alarm for abuse detection (AC10)
    const emailsRejectedAlarm = new cloudwatch.Alarm(this, 'EmailsRejectedAlarm', {
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
      // Issue #969: EmailsRejected is a custom metric emitted only when the forwarder
      // runs, so quiet periods would otherwise flap the alarm to INSUFFICIENT_DATA.
      // Zero rejections is the healthy state.
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // #1001: both actions, not just ALARM. Without the OK action the github-issues Lambda can
    // never close the issue it opened (#956), so a single abuse spike would leave an incident
    // issue open forever. Guarded by a test that walks every alarm in this stack.
    // A message in the forwarding DLQ is a mail that reached nobody after three attempts.
    const forwardingDlqAlarm = new cloudwatch.Alarm(this, 'ForwardingDlqAlarm', {
      alarmName: `batbern-${envName}-forwarding-dlq`,
      alarmDescription:
        'Email forwarding: a mail to a batbern.ch alias failed three times and sits in the DLQ (replayable from S3 for 7 days)',
      metric: forwardingDlq.metricApproximateNumberOfMessagesVisible({
        period: cdk.Duration.minutes(5),
        statistic: 'Maximum',
      }),
      threshold: 0,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // Story 16.0 (#1160): EMS forwards replies@ mail that matches no keyword to the organizers and
    // rethrows a failure before the first send, so SQS retries three times and dead-letters it.
    // A message here is a human reply that reached nobody (raw mail stays in S3 for 7 days).
    const repliesDlqAlarm = new cloudwatch.Alarm(this, 'RepliesDlqAlarm', {
      alarmName: `batbern-${envName}-replies-dlq`,
      alarmDescription:
        'Inbound replies: a mail to replies@ failed three times and sits in the DLQ (replayable from S3 for 7 days)',
      metric: dlq.metricApproximateNumberOfMessagesVisible({
        period: cdk.Duration.minutes(5),
        statistic: 'Maximum',
      }),
      threshold: 0,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    // EMS counts EmailsFailed when copies could not be sent after the point of no return, and when
    // a redelivery finds a claim that stalled mid-send. Neither is retried, so neither reaches the
    // DLQ: this alarm is their only signal.
    const forwardingFailedAlarm = new cloudwatch.Alarm(this, 'ForwardingFailedAlarm', {
      alarmName: `batbern-${envName}-forwarding-failed`,
      alarmDescription:
        'Email forwarding: forwarded copies failed or a forward stalled mid-send; some recipients may be missing a mail',
      metric: new cloudwatch.Metric({
        namespace: 'BATbern/EmailForwarder',
        metricName: 'EmailsFailed',
        statistic: 'Sum',
        period: cdk.Duration.hours(1),
      }),
      threshold: 0,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
    });

    if (props.alarmTopic) {
      const alarmAction = new cloudwatchActions.SnsAction(props.alarmTopic);
      for (const alarm of [
        emailsRejectedAlarm,
        forwardingDlqAlarm,
        repliesDlqAlarm,
        forwardingFailedAlarm,
      ]) {
        alarm.addAlarmAction(alarmAction);
        alarm.addOkAction(alarmAction);
      }
    }

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
      forwardingQueue.grantConsumeMessages(props.emsTaskRole);
      inboundBucket.grantRead(props.emsTaskRole);
    }

    // CloudFormation outputs for EMS env var injection
    new cdk.CfnOutput(this, 'InboundQueueUrl', {
      value: inboundQueue.queueUrl,
      exportName: `${id}-InboundQueueUrl`,
    });
    new cdk.CfnOutput(this, 'ForwardingQueueUrl', {
      value: forwardingQueue.queueUrl,
      exportName: `${id}-ForwardingQueueUrl`,
    });
    new cdk.CfnOutput(this, 'InboundBucketName', {
      value: inboundBucket.bucketName,
      exportName: `${id}-InboundBucketName`,
    });
  }
}
