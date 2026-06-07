import * as cdk from 'aws-cdk-lib';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as ses from 'aws-cdk-lib/aws-ses';
import * as subscriptions from 'aws-cdk-lib/aws-sns-subscriptions';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface SesStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * SES Stack - Newsletter bounce/complaint processing pipeline (Story 10.29)
 *
 * Data flow:
 *   Newsletter email → SES Configuration Set → BOUNCE/COMPLAINT event
 *   → SNS Topic → SQS Queue → @SqsListener in EMS (BounceProcessingService)
 *
 * Also reserved for future SES templates if needed.
 */
export class SesStack extends cdk.Stack {
  public readonly bounceQueue: sqs.Queue;
  public readonly bounceQueueDlq: sqs.Queue;
  /**
   * Name of the SES Configuration Set that routes BOUNCE/COMPLAINT events to SNS → SQS.
   * Consumers (EventManagementStack) inject this as BATBERN_SES_CONFIGURATION_SET_NAME so
   * each SendEmail attaches the configuration set; without it, SES drops the events
   * silently and the app's BounceProcessingService never sees them.
   */
  public readonly configurationSetName: string;

  constructor(scope: Construct, id: string, props: SesStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'Email');
    cdk.Tags.of(this).add('Project', 'BATbern');

    // Story 10.29 AC2: SES Configuration Set for newsletter sends
    this.configurationSetName = `batbern-${envName}-newsletter`;
    const configSet = new ses.CfnConfigurationSet(this, 'NewsletterConfigSet', {
      name: this.configurationSetName,
    });

    // Story 10.29 AC2: SNS Topic for BOUNCE + COMPLAINT events
    const bounceTopic = new sns.Topic(this, 'BounceTopic', {
      topicName: `batbern-${envName}-ses-bounces`,
    });

    // Story 10.29 AC2: Dead-letter queue for failed bounce processing (5 retries before DLQ)
    this.bounceQueueDlq = new sqs.Queue(this, 'BounceProcessingDLQ', {
      queueName: `batbern-${envName}-bounce-processing-dlq`,
      retentionPeriod: cdk.Duration.days(14),
    });

    // Story 10.29 AC2: SQS Queue for bounce processing
    this.bounceQueue = new sqs.Queue(this, 'BounceProcessingQueue', {
      queueName: `batbern-${envName}-bounce-processing`,
      visibilityTimeout: cdk.Duration.seconds(300),
      deadLetterQueue: { queue: this.bounceQueueDlq, maxReceiveCount: 5 },
    });

    // Subscribe SQS queue to SNS topic
    bounceTopic.addSubscription(new subscriptions.SqsSubscription(this.bounceQueue));

    // Story 10.29 AC2: SES Event Destination routing BOUNCE + COMPLAINT to SNS
    new ses.CfnConfigurationSetEventDestination(this, 'BounceEventDest', {
      configurationSetName: configSet.ref,
      eventDestination: {
        name: 'bounce-complaint-notifications',
        enabled: true,
        matchingEventTypes: ['bounce', 'complaint'],
        snsDestination: { topicArn: bounceTopic.topicArn },
      },
    });

    // CloudFormation outputs
    new cdk.CfnOutput(this, 'BounceQueueUrl', {
      value: this.bounceQueue.queueUrl,
      exportName: `${id}-BounceQueueUrl`,
    });
  }
}
