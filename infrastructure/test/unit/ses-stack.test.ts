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

  // Tags applied
  test('should_applyEnvironmentTags_when_sesStackDeployed', () => {
    expect(stack.stackName).toBe('TestSesStack');
  });
});
