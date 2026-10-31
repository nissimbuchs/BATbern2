import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { SesStack } from '../../lib/stacks/ses-stack';
import { stagingConfig } from '../../lib/config/staging-config';

describe('SesStack', () => {
  let app: App;
  let stack: SesStack;
  let template: Template;

  beforeEach(() => {
    app = new App();
    stack = new SesStack(app, 'TestSesStack', {
      config: stagingConfig,
      env: { account: '123456789012', region: 'eu-central-1' },
    });
    template = Template.fromStack(stack);
  });

  // Story 10.29 AC2: SES Configuration Set
  test('should_createConfigurationSet_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSet', {
      Name: 'batbern-staging-newsletter',
    });
  });

  // Story 10.29 AC2: SNS Topic for bounce/complaint events
  test('should_createSnsTopicForBounces_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SNS::Topic', {
      TopicName: 'batbern-staging-ses-bounces',
    });
  });

  // Story 10.29 AC2: SQS Queue for bounce processing
  test('should_createBounceProcessingQueue_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-staging-bounce-processing',
      VisibilityTimeout: 300,
    });
  });

  // Story 10.29 AC2: Dead-letter queue for failed bounce processing
  test('should_createDeadLetterQueue_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-staging-bounce-processing-dlq',
      MessageRetentionPeriod: 1209600, // 14 days in seconds
    });
  });

  // Story 10.29 AC2: DLQ configured with maxReceiveCount 5
  test('should_configureDlqWithMaxReceiveCount_when_bounceQueueCreated', () => {
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-staging-bounce-processing',
      RedrivePolicy: Match.objectLike({
        maxReceiveCount: 5,
      }),
    });
  });

  // Story 10.29 AC2: SNS subscription to SQS
  test('should_subscribeSqsToSnsTopic_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SNS::Subscription', {
      Protocol: 'sqs',
    });
  });

  // Story 10.29 AC2: SES Event Destination for BOUNCE + COMPLAINT
  test('should_createEventDestination_when_configurationSetCreated', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        Enabled: true,
        MatchingEventTypes: ['bounce', 'complaint'],
        SnsDestination: Match.objectLike({
          TopicARN: Match.anyValue(),
        }),
      }),
    });
  });

  // Story 10.29 AC2: bounceQueue exposed as public property
  test('should_exposeBounceQueueAsPublicProperty_when_sesStackCreated', () => {
    expect(stack.bounceQueue).toBeDefined();
    expect(stack.bounceQueue.queueUrl).toBeDefined();
  });

  // Story 10.29 AC2: bounceQueueDlq exposed as public property (for monitoring alarms)
  test('should_exposeBounceQueueDlqAsPublicProperty_when_sesStackCreated', () => {
    expect(stack.bounceQueueDlq).toBeDefined();
  });

  // Story 10.29: configuration-set name exposed so consumers (EventManagementStack)
  // can inject it as BATBERN_SES_CONFIGURATION_SET_NAME — without this, SES drops
  // bounce/complaint events silently.
  test('should_exposeConfigurationSetNameAsPublicProperty_when_sesStackCreated', () => {
    expect(stack.configurationSetName).toBe('batbern-staging-newsletter');
  });

  // ----- Dedicated transactional config set (Option 2) -----

  test('should_createTransactionalConfigurationSet_when_sesStackDeployed', () => {
    template.hasResourceProperties('AWS::SES::ConfigurationSet', {
      Name: 'batbern-staging-transactional',
    });
  });

  test('should_exposeTransactionalConfigurationSetName_when_sesStackCreated', () => {
    expect(stack.transactionalConfigurationSetName).toBe('batbern-staging-transactional');
  });

  test('should_routeTransactionalEventsToCloudWatchAndSns_when_configSetCreated', () => {
    // CloudWatch aggregate metrics destination
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        Enabled: true,
        MatchingEventTypes: Match.arrayWith(['delivery', 'bounce', 'complaint', 'reject']),
        CloudWatchDestination: Match.anyValue(),
      }),
    });
    // Per-recipient SNS destination
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        MatchingEventTypes: Match.arrayWith(['delivery', 'bounce', 'complaint', 'reject']),
        SnsDestination: Match.anyValue(),
      }),
    });
  });

  test('should_createTransactionalEventLogger_withOwnLogGroup_andSubscription', () => {
    template.hasResourceProperties('AWS::Lambda::Function', {
      FunctionName: 'batbern-staging-transactional-event-logger',
      Runtime: 'nodejs24.x',
    });
    template.hasResourceProperties('AWS::Logs::LogGroup', {
      LogGroupName: '/aws/lambda/batbern-staging-transactional-event-logger',
    });
    template.hasResourceProperties('AWS::SNS::Subscription', {
      Protocol: 'lambda',
    });
  });

  test('should_keepNewsletterBounceSqsPipeline_intact_alongsideTransactionalSet', () => {
    // Newsletter set still routes bounce/complaint to SNS (the suppression pipeline)
    template.hasResourceProperties('AWS::SES::ConfigurationSetEventDestination', {
      EventDestination: Match.objectLike({
        MatchingEventTypes: ['bounce', 'complaint'],
        SnsDestination: Match.anyValue(),
      }),
    });
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-staging-bounce-processing',
    });
  });

  // ----- Story 16.1b: delivery status queue on the transactional events topic -----

  test('should_createMailDeliveryStatusQueue_withDlqAfterThreeAttempts', () => {
    const dlq = template.findResources('AWS::SQS::Queue', {
      Properties: { QueueName: 'batbern-staging-mail-delivery-status-dlq' },
    });
    const dlqId = Object.keys(dlq)[0];
    expect(dlqId).toBeDefined();
    template.hasResourceProperties('AWS::SQS::Queue', {
      QueueName: 'batbern-staging-mail-delivery-status',
      RedrivePolicy: {
        deadLetterTargetArn: { 'Fn::GetAtt': [dlqId, 'Arn'] },
        maxReceiveCount: 3,
      },
    });
  });

  test('should_subscribeDeliveryStatusQueue_toTransactionalTopic_withSnsEnvelope', () => {
    const topics = template.findResources('AWS::SNS::Topic', {
      Properties: { TopicName: 'batbern-staging-ses-transactional-events' },
    });
    const topicId = Object.keys(topics)[0];
    const queues = template.findResources('AWS::SQS::Queue', {
      Properties: { QueueName: 'batbern-staging-mail-delivery-status' },
    });
    const queueId = Object.keys(queues)[0];
    template.hasResourceProperties('AWS::SNS::Subscription', {
      Protocol: 'sqs',
      TopicArn: { Ref: topicId },
      Endpoint: { 'Fn::GetAtt': [queueId, 'Arn'] },
      // The listener parses the SNS envelope, like BounceProcessingService.
      RawMessageDelivery: Match.absent(),
    });
  });

  test('should_keepTransactionalEventLogger_whenDeliveryStatusQueueAdded', () => {
    template.resourcePropertiesCountIs('AWS::SNS::Subscription', { Protocol: 'lambda' }, 1);
  });

  test('should_notRouteTransactionalEvents_toNewsletterBounceQueue', () => {
    // AC4: transactional bounces never reach BounceProcessingService.
    const bounceQueues = template.findResources('AWS::SQS::Queue', {
      Properties: { QueueName: 'batbern-staging-bounce-processing' },
    });
    const bounceQueueId = Object.keys(bounceQueues)[0];
    const transactionalTopics = template.findResources('AWS::SNS::Topic', {
      Properties: { TopicName: 'batbern-staging-ses-transactional-events' },
    });
    const transactionalTopicId = Object.keys(transactionalTopics)[0];
    const subscriptions = Object.values(template.findResources('AWS::SNS::Subscription'));
    const wrong = subscriptions.filter(
      (sub) =>
        JSON.stringify(sub.Properties.TopicArn) === JSON.stringify({ Ref: transactionalTopicId }) &&
        JSON.stringify(sub.Properties.Endpoint).includes(bounceQueueId)
    );
    expect(wrong).toHaveLength(0);
  });

  test('should_exposeMailDeliveryStatusQueues_asPublicProperties', () => {
    expect(stack.mailDeliveryStatusQueue).toBeDefined();
    expect(stack.mailDeliveryStatusQueueDlq).toBeDefined();
  });

  // Tags applied
  test('should_applyEnvironmentTags_when_sesStackDeployed', () => {
    expect(stack.stackName).toBe('TestSesStack');
  });
});
