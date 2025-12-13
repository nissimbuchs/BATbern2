import * as cdk from 'aws-cdk-lib';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import { EnvironmentConfig } from '../config/environment-config';

export interface AutoShutdownStackProps extends cdk.StackProps {
  config: EnvironmentConfig;
  clusterName: string;
  rdsClusterIdentifier?: string;
}

/**
 * Auto-Shutdown Stack - Schedules automatic shutdown/startup for dev environment
 *
 * Cost Optimization Priority 5: Dev Environment Auto-Shutdown
 *
 * Schedules:
 * - Shutdown: 8 PM UTC (weekdays), 6 PM UTC (Fridays)
 * - Startup: 8 AM UTC (weekdays)
 * - Weekend: Shut down all weekend
 *
 * Targets:
 * - ECS services (scale to 0)
 * - RDS instances (stop)
 *
 * Expected Savings: ~70% of dev environment costs (~$66/month)
 *
 * IMPORTANT: Only deployed to development environment
 */
export class AutoShutdownStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: AutoShutdownStackProps) {
    super(scope, id, props);

    const envName = props.config.envName;

    // Only create auto-shutdown for development environment
    if (envName !== 'development') {
      return;
    }

    // Lambda function to scale ECS services
    const ecsScalerFunction = new lambda.Function(this, 'EcsScalerFunction', {
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'index.handler',
      code: lambda.Code.fromInline(`
import boto3
import os

ecs = boto3.client('ecs')
rds = boto3.client('rds')

def handler(event, context):
    cluster_name = os.environ['CLUSTER_NAME']
    rds_instance = os.environ.get('RDS_INSTANCE')
    action = event.get('action', 'shutdown')

    # List all services in cluster
    response = ecs.list_services(cluster=cluster_name)
    service_arns = response['serviceArns']

    if action == 'shutdown':
        # Scale all services to 0
        for service_arn in service_arns:
            service_name = service_arn.split('/')[-1]
            print(f"Scaling down {service_name} to 0")
            ecs.update_service(
                cluster=cluster_name,
                service=service_name,
                desiredCount=0
            )

        # Stop RDS instance if configured
        if rds_instance:
            try:
                print(f"Stopping RDS instance {rds_instance}")
                rds.stop_db_instance(DBInstanceIdentifier=rds_instance)
            except Exception as e:
                print(f"Error stopping RDS: {e}")
                # Continue even if RDS stop fails (might already be stopped)

    elif action == 'startup':
        # Start RDS instance first (if configured)
        if rds_instance:
            try:
                print(f"Starting RDS instance {rds_instance}")
                rds.start_db_instance(DBInstanceIdentifier=rds_instance)
            except Exception as e:
                print(f"Error starting RDS: {e}")
                # Continue even if RDS start fails (might already be running)

        # Scale all services to 1
        for service_arn in service_arns:
            service_name = service_arn.split('/')[-1]
            print(f"Scaling up {service_name} to 1")
            ecs.update_service(
                cluster=cluster_name,
                service=service_name,
                desiredCount=1
            )

    return {
        'statusCode': 200,
        'body': f'Successfully executed {action} for {len(service_arns)} services'
    }
`),
      environment: {
        CLUSTER_NAME: props.clusterName,
        ...(props.rdsClusterIdentifier && {
          RDS_INSTANCE: props.rdsClusterIdentifier,
        }),
      },
      timeout: cdk.Duration.minutes(5),
      description: 'Scales ECS services and stops/starts RDS for dev environment cost savings',
    });

    // Grant permissions to scale ECS services
    ecsScalerFunction.addToRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'ecs:ListServices',
        'ecs:DescribeServices',
        'ecs:UpdateService',
      ],
      resources: ['*'],
    }));

    // Grant permissions to stop/start RDS
    if (props.rdsClusterIdentifier) {
      ecsScalerFunction.addToRolePolicy(new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: [
          'rds:StopDBInstance',
          'rds:StartDBInstance',
          'rds:DescribeDBInstances',
        ],
        resources: [
          `arn:aws:rds:${props.config.region}:${cdk.Stack.of(this).account}:db:${props.rdsClusterIdentifier}`,
        ],
      }));
    }

    // Schedule: Shutdown at 23:59 UTC Monday-Thursday
    const shutdownWeekdayRule = new events.Rule(this, 'ShutdownWeekdayRule', {
      schedule: events.Schedule.cron({
        minute: '59',
        hour: '23',
        weekDay: 'MON-THU',
      }),
      description: 'Shutdown dev environment at 23:59 UTC on weekdays',
    });

    shutdownWeekdayRule.addTarget(new targets.LambdaFunction(ecsScalerFunction, {
      event: events.RuleTargetInput.fromObject({ action: 'shutdown' }),
    }));

    // Schedule: Shutdown at 23:59 UTC on Friday to Sunday (early weekend start)
    const shutdownFridayRule = new events.Rule(this, 'ShutdownFridayRule', {
      schedule: events.Schedule.cron({
        minute: '59',
        hour: '23',
        weekDay: 'FRI-SUN',
      }),
      description: 'Shutdown dev environment at 23:59 UTC on Friday to Sunday',
    });

    shutdownFridayRule.addTarget(new targets.LambdaFunction(ecsScalerFunction, {
      event: events.RuleTargetInput.fromObject({ action: 'shutdown' }),
    }));

    // Schedule: Startup at 8 AM UTC Monday-Friday
    const startupRule = new events.Rule(this, 'StartupRule', {
      schedule: events.Schedule.cron({
        minute: '0',
        hour: '8',
        weekDay: 'MON-SUN',
      }),
      description: 'Startup dev environment at 8 AM UTC on weekdays and weekends',
    });

    startupRule.addTarget(new targets.LambdaFunction(ecsScalerFunction, {
      event: events.RuleTargetInput.fromObject({ action: 'startup' }),
    }));

    // Outputs
    new cdk.CfnOutput(this, 'ScalerFunctionArn', {
      value: ecsScalerFunction.functionArn,
      description: 'Lambda function ARN for manual scaling',
      exportName: `${envName}-EcsScalerFunctionArn`,
    });

    new cdk.CfnOutput(this, 'ShutdownSchedule', {
      value: 'Mon-Sun: 23:59 UTC',
      description: 'Shutdown schedule for dev environment',
    });

    new cdk.CfnOutput(this, 'StartupSchedule', {
      value: 'Mon-Sun: 08:00 UTC',
      description: 'Startup schedule for dev environment',
    });

    // Apply tags
    cdk.Tags.of(this).add('Environment', envName);
    cdk.Tags.of(this).add('Component', 'AutoShutdown');
    cdk.Tags.of(this).add('Project', 'BATbern');
    cdk.Tags.of(this).add('CostOptimization', 'Priority5');
  }
}
