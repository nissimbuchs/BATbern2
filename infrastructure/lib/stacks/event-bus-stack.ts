import * as cdk from 'aws-cdk-lib';
import * as events from 'aws-cdk-lib/aws-events';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface EventBusStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
}

/**
 * EventBus Stack - Provides EventBridge event bus for domain events
 *
 * Implements event-driven architecture for microservices communication
 */
export class EventBusStack extends cdk.Stack {
  public readonly eventBus: events.EventBus;

  constructor(scope: Construct, id: string, props: EventBusStackProps) {
    super(scope, id, props);

    // Create EventBridge Bus for Domain Events
    this.eventBus = new events.EventBus(this, 'EventBus', {
      eventBusName: `batbern-${props.config.envName}`,
      description: `BATbern Domain Events - ${props.config.envName}`,
    });

    // Apply tags
    cdk.Tags.of(this.eventBus).add('Environment', props.config.envName);
    cdk.Tags.of(this.eventBus).add('Component', 'EventBus');
    cdk.Tags.of(this.eventBus).add('Project', 'BATbern');

    // Outputs
    new cdk.CfnOutput(this, 'EventBusName', {
      value: this.eventBus.eventBusName,
      description: 'EventBridge event bus name',
      exportName: `${props.config.envName}-EventBusName`,
    });

    new cdk.CfnOutput(this, 'EventBusArn', {
      value: this.eventBus.eventBusArn,
      description: 'EventBridge event bus ARN',
      exportName: `${props.config.envName}-EventBusArn`,
    });
  }
}
