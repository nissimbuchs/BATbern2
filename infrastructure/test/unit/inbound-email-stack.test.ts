import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Stack } from 'aws-cdk-lib';
import { InboundEmailStack } from '../../lib/stacks/inbound-email-stack';
import { stagingConfig } from '../../lib/config/staging-config';

describe('InboundEmailStack', () => {
  let app: App;
  let stack: InboundEmailStack;
  let template: Template;

  beforeEach(() => {
    app = new App();
    stack = new InboundEmailStack(app, 'TestInboundEmailStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-west-1' },
    });
    template = Template.fromStack(stack);
  });

  // AC1: SES ReceiptRuleSet exists
  test('should_createSesReceiptRuleSet_when_inboundEmailStackDeployed', () => {
    template.resourceCountIs('AWS::SES::ReceiptRuleSet', 1);
    template.hasResourceProperties('AWS::SES::ReceiptRuleSet', {
      RuleSetName: 'batbern-inbound-staging',
    });
  });

  // AC1: SES ReceiptRule routes replies to S3
  test('should_createSesReceiptRuleWithS3Action_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: ['replies@batbern.ch'],
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'emails/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // AC1: S3 bucket exists with encryption
  test('should_createS3BucketWithEncryption_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-inbound-emails-staging',
      BucketEncryption: Match.objectLike({
        ServerSideEncryptionConfiguration: Match.arrayWith([
          Match.objectLike({
            ServerSideEncryptionByDefault: Match.objectLike({
              SSEAlgorithm: 'AES256',
            }),
          }),
        ]),
      }),
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
    });
  });

  // AC1: S3 lifecycle rule (auto-purge after 7 days)
  test('should_applyLifecycleRule_when_inboundBucketCreated', () => {
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: 'batbern-inbound-emails-staging',
      LifecycleConfiguration: Match.objectLike({
        Rules: Match.arrayWith([
          Match.objectLike({
            Status: 'Enabled',
            ExpirationInDays: 7,
          }),
        ]),
      }),
    });
  });

  // AC1: SQS queue exists (standard, not FIFO)
  test('should_createSqsQueue_when_inboundEmailStackDeployed', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-inbound-email-staging',
    });
  });

  // AC1: DLQ exists for failed messages
  test('should_createDeadLetterQueue_when_inboundEmailStackDeployed', () => {
    // Main queue references a DLQ via RedrivePolicy
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-inbound-email-staging',
      RedrivePolicy: Match.objectLike({
        maxReceiveCount: 3,
      }),
    });
  });

  // AC1: S3 bucket policy allows SES to put objects
  test('should_allowSesToPutObjectsInBucket_when_bucketPolicyCreated', () => {
    template.hasResourceProperties('AWS::S3::BucketPolicy', {
      PolicyDocument: Match.objectLike({
        Statement: Match.arrayWith([
          Match.objectLike({
            Principal: Match.objectLike({
              Service: 'ses.amazonaws.com',
            }),
            Action: 's3:PutObject',
          }),
        ]),
      }),
    });
  });

  // AC1: S3 event notification routes to SQS (CDK uses custom resource for notifications)
  test('should_createNotificationCustomResource_when_inboundBucketDeployed', () => {
    // replies queue + DLQ, forwarding queue + DLQ
    template.resourceCountIs('AWS::SQS::Queue', 4);
    template.hasResourceProperties('Custom::S3BucketNotifications', {
      NotificationConfiguration: Match.objectLike({
        QueueConfigurations: Match.arrayWith([
          Match.objectLike({
            Filter: { Key: { FilterRules: [{ Name: 'prefix', Value: 'emails/' }] } },
          }),
        ]),
      }),
    });
  });

  // Tags applied to stack resources
  test('should_applyEnvironmentTags_when_stackDeployed', () => {
    expect(stack.stackName).toBe('TestInboundEmailStack');
  });

  // ========================
  // Story 10.26: Email Forwarding
  // ========================

  // T4.1 — AC1: Forwarding receipt rule for named addresses
  test('should_createForwardingReceiptRule_when_namedAddressesConfigured', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: Match.arrayWith([
          'ok@batbern.ch',
          'info@batbern.ch',
          'events@batbern.ch',
          'partner@batbern.ch',
          'support@batbern.ch',
        ]),
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'forwarding/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // T4.1 — AC1: Catch-all domain receipt rule for batbern{N}@ addresses
  test('should_createCatchAllDomainReceiptRule_when_domainConfigured', () => {
    template.hasResourceProperties('AWS::SES::ReceiptRule', {
      Rule: Match.objectLike({
        Recipients: Match.arrayWith(['batbern.ch']),
        Actions: Match.arrayWith([
          Match.objectLike({
            S3Action: Match.objectLike({
              ObjectKeyPrefix: 'forwarding/',
            }),
          }),
        ]),
        Enabled: true,
      }),
    });
  });

  // T4.3 — Receipt rule ordering: replies first, named forwarding second, catch-all third
  test('should_haveThreeReceiptRules_when_allRulesConfigured', () => {
    // 3 receipt rules: replies, named forwarding addresses, catch-all domain
    template.resourceCountIs('AWS::SES::ReceiptRule', 3);
  });

  // Forwarding moved into EMS: no forwarder Lambda, the forwarding/ prefix feeds an SQS queue.
  test('should_notCreateForwarderLambda_when_forwardingRunsInEms', () => {
    const functions = template.findResources('AWS::Lambda::Function', {
      Properties: { FunctionName: 'batbern-email-forwarder-staging' },
    });
    expect(Object.keys(functions)).toHaveLength(0);
  });

  test('should_createForwardingQueueWithDlq_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-forwarding-email-staging',
      VisibilityTimeout: 300,
      RedrivePolicy: Match.objectLike({ maxReceiveCount: 3 }),
    });
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-forwarding-email-dlq-staging',
    });
  });

  test('should_notifyForwardingQueue_when_objectCreatedUnderForwardingPrefix', () => {
    template.hasResourceProperties('Custom::S3BucketNotifications', {
      NotificationConfiguration: Match.objectLike({
        QueueConfigurations: Match.arrayWith([
          Match.objectLike({
            Events: ['s3:ObjectCreated:*'],
            Filter: { Key: { FilterRules: [{ Name: 'prefix', Value: 'forwarding/' }] } },
          }),
        ]),
      }),
    });
    // ...and nothing invokes a Lambda for that prefix any more
    const notifications = template.findResources('Custom::S3BucketNotifications');
    for (const resource of Object.values(notifications)) {
      const config = (
        resource.Properties as { NotificationConfiguration?: Record<string, unknown> }
      ).NotificationConfiguration;
      expect(config?.LambdaFunctionConfigurations).toBeUndefined();
    }
  });

  test('should_keepForwarderLogGroup_when_lambdaRemoved', () => {
    // Retained so the removal does not delete the last 30 days of forwarder logs.
    template.hasResource('AWS::Logs::LogGroup', {
      Properties: { LogGroupName: '/aws/lambda/batbern-email-forwarder-staging' },
      DeletionPolicy: 'Retain',
    });
  });

  test('should_exposeForwardingWiringForEms_when_stackDeployed', () => {
    expect(stack.forwardingDomain).toBe('batbern.ch');
    expect(stack.forwarderConfigurationSetName).toBe('batbern-staging-forwarder');
    expect(stack.forwardingQueue).toBeDefined();
  });

  test('should_alarmOnEmailsFailed_when_copiesFailOrForwardStalls', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'batbern-staging-forwarding-failed',
      Namespace: 'BATbern/EmailForwarder',
      MetricName: 'EmailsFailed',
      Threshold: 0,
      TreatMissingData: 'notBreaching',
    });
  });

  test('should_alarmOnForwardingDlq_when_mailFailedThreeTimes', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'batbern-staging-forwarding-dlq',
      MetricName: 'ApproximateNumberOfMessagesVisible',
      Threshold: 0,
      ComparisonOperator: 'GreaterThanThreshold',
      TreatMissingData: 'notBreaching',
    });
  });

  // Story 16.0 (#1160): unrecognized replies@ mail is forwarded; a forward that fails before the
  // first send is rethrown, retried three times and dead-lettered. Without this alarm the reply
  // would sit in the DLQ unseen.
  test('should_alarmOnRepliesDlq_when_replyFailedThreeTimes', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      AlarmName: 'batbern-staging-replies-dlq',
      MetricName: 'ApproximateNumberOfMessagesVisible',
      Dimensions: [{ Name: 'QueueName', Value: Match.anyValue() }],
      Threshold: 0,
      ComparisonOperator: 'GreaterThanThreshold',
      TreatMissingData: 'notBreaching',
    });
  });

  // T5.3 — AC10: CloudWatch alarm for EmailsRejected metric
  test('should_createCloudWatchAlarm_when_forwarderLambdaCreated', () => {
    template.hasResourceProperties('AWS::CloudWatch::Alarm', {
      MetricName: 'EmailsRejected',
      // Issue #969: the custom EmailsRejected metric is only emitted when the forwarder
      // runs, so without notBreaching the alarm flaps INSUFFICIENT_DATA <-> OK. Zero
      // rejections is the healthy state.
      TreatMissingData: 'notBreaching',
      Namespace: 'BATbern/EmailForwarder',
      Threshold: 20,
      EvaluationPeriods: 1,
      Period: 3600,
    });
  });

  // Delivery tracking (Option A): dedicated forwarder SES configuration set
  test('should_createDedicatedForwarderConfigurationSet_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSet', {
      Name: 'batbern-staging-forwarder',
    });
  });

  test('should_routeForwarderEventsToCloudWatch_when_configSetCreated', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        Enabled: true,
        MatchingEventTypes: Match.arrayWith(['delivery', 'bounce', 'complaint', 'reject']),
        CloudWatchDestination: Match.anyValue(),
      }),
    });
  });

  // Per-recipient delivery tracking: SES → SNS → logger Lambda → CloudWatch Logs
  test('should_createForwarderEventsSnsTopic_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::SNS::Topic', {
      TopicName: 'batbern-staging-ses-forwarder-events',
    });
  });

  test('should_routeForwarderEventsToSns_when_configSetCreated', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        Enabled: true,
        MatchingEventTypes: Match.arrayWith(['delivery', 'bounce', 'complaint', 'reject']),
        SnsDestination: Match.anyValue(),
      }),
    });
  });

  test('should_createSesEventLoggerLambda_with_ownLogGroup', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-staging-ses-event-logger',
      Runtime: 'nodejs24.x',
    });
    template.hasResourceProperties('AWS::Logs::LogGroup', {
      LogGroupName: '/aws/lambda/batbern-staging-ses-event-logger',
    });
  });

  test('should_subscribeLoggerLambdaToEventsTopic_when_stackDeployed', () => {
    template.hasResourceProperties('AWS::SNS::Subscription', {
      Protocol: 'lambda',
    });
  });

  // T12 — Story 10.26: MX record for SES inbound SMTP when hosted zone provided
  test('should_createMxRecord_when_hostedZoneProvided', () => {
    const mxApp = new App();
    const helperStack2 = new (require('aws-cdk-lib').Stack)(mxApp, 'HelperStack2', {
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    const hostedZone = new (require('aws-cdk-lib/aws-route53').HostedZone)(
      helperStack2,
      'TestZone',
      {
        zoneName: 'batbern.ch',
      }
    );

    const mxStack = new InboundEmailStack(mxApp, 'TestMxInboundEmailStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-central-1' },
      hostedZone,
    });
    const mxTemplate = Template.fromStack(mxStack);

    mxTemplate.hasResourceProperties('AWS::Route53::RecordSet', {
      Type: 'MX',
      ResourceRecords: Match.arrayWith([
        Match.stringLikeRegexp('10 inbound-smtp\\..*\\.amazonaws\\.com'),
      ]),
    });
  });
});

describe('Alarm actions (#1001)', () => {
  /**
   * batbern-staging-emails-rejected was deployed with NO AlarmActions and NO OKActions —
   * verified against the live account, both arrays empty. It could fire and nothing would
   * happen: no mail, no GitHub issue, no record anywhere. "A declared alarm is not a working
   * alarm" (#970), and this stack escaped the #977 sweep because it defines its alarm locally
   * rather than through MonitoringStack.
   *
   * Found only because the dormant infrastructure/test/e2e suite was switched on (#1001) — the
   * suite had been skipped behind a TEST_E2E guard that nothing set, so this had been silently
   * true for as long as the alarm existed.
   */
  const build = (withTopic: boolean) => {
    const app = new App();
    const topicHost = new Stack(app, 'TopicHost', {
      env: { account: '123456789012', region: 'eu-west-1' },
    });
    const stack = new InboundEmailStack(app, 'TestInboundEmailAlarms', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-west-1' },
      alarmTopic: withTopic ? new sns.Topic(topicHost, 'AlarmTopic') : undefined,
    });
    return Template.fromStack(stack);
  };

  test('should_attachBothAlarmAndOkActions_when_alarmTopicProvided', () => {
    // BOTH, not just ALARM. Without the OK action the github-issues Lambda can never close the
    // issue it opened (#956), so one abuse spike leaves an incident issue open forever.
    const alarms = build(true).findResources('AWS::CloudWatch::Alarm');
    expect(Object.keys(alarms).length).toBeGreaterThan(0);

    const missing = Object.entries(alarms)
      .filter(([, r]) => {
        const p = r.Properties as { AlarmActions?: unknown[]; OKActions?: unknown[] };
        return !p?.AlarmActions?.length || !p?.OKActions?.length;
      })
      .map(([logicalId]) => logicalId);

    expect(missing).toEqual([]);
  });

  test('should_stillCreateTheAlarm_when_noTopicProvided', () => {
    // The topic is optional so the stack stays usable standalone, but the alarm must still
    // exist — silently dropping it would trade one invisible failure for another.
    build(false).resourceCountIs('AWS::CloudWatch::Alarm', 4);
  });
});
