import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as customResources from 'aws-cdk-lib/custom-resources';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';

/**
 * Bootstrap Organizer Construct
 *
 * Creates initial organizer user in Cognito for environment bootstrapping.
 * Implements AC7: Bootstrap organizer creation works for all environments
 *
 * Features:
 * - Creates bootstrap user via AdminCreateUser
 * - Sets permanent password (no forced password change)
 * - Assigns ORGANIZER role via custom attribute
 * - Sets email_verified to true for immediate login
 * - Idempotent - safe to deploy multiple times
 */

export interface BootstrapOrganizerProps {
  userPool: cognito.IUserPool;
  email: string;
  password: string;
}

export class BootstrapOrganizer extends Construct {
  constructor(scope: Construct, id: string, props: BootstrapOrganizerProps) {
    super(scope, id);

    // Create log groups for custom resources
    const createUserLogGroup = new logs.LogGroup(this, 'CreateUserLogGroup', {
      logGroupName: `/aws/lambda/bootstrap-organizer-create-user`,
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const setPasswordLogGroup = new logs.LogGroup(this, 'SetPasswordLogGroup', {
      logGroupName: `/aws/lambda/bootstrap-organizer-set-password`,
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // Create bootstrap organizer user via custom resource
    const createUserCustomResource = new customResources.AwsCustomResource(
      this,
      'CreateBootstrapUser',
      {
        onCreate: {
          service: 'CognitoIdentityServiceProvider',
          action: 'adminCreateUser',
          parameters: {
            UserPoolId: props.userPool.userPoolId,
            Username: props.email,
            MessageAction: 'SUPPRESS', // Don't send welcome email
            UserAttributes: [
              {
                Name: 'email',
                Value: props.email,
              },
              {
                Name: 'email_verified',
                Value: 'true',
              },
              {
                Name: 'custom:role',
                Value: 'organizer',
              },
            ],
          },
          physicalResourceId: customResources.PhysicalResourceId.of(
            `bootstrap-organizer-${props.email}`
          ),
        },
        onUpdate: {
          // Idempotent - only check if user exists, don't create again
          service: 'CognitoIdentityServiceProvider',
          action: 'adminGetUser',
          parameters: {
            UserPoolId: props.userPool.userPoolId,
            Username: props.email,
          },
          physicalResourceId: customResources.PhysicalResourceId.of(
            `bootstrap-organizer-${props.email}`
          ),
          // Ignore error if user doesn't exist (will be created on next deploy)
          ignoreErrorCodesMatching: 'UserNotFoundException',
        },
        policy: customResources.AwsCustomResourcePolicy.fromSdkCalls({
          resources: customResources.AwsCustomResourcePolicy.ANY_RESOURCE,
        }),
        logGroup: createUserLogGroup,
      }
    );

    // Set permanent password (removes temporary status)
    const setPermanentPassword = new customResources.AwsCustomResource(
      this,
      'SetPermanentPassword',
      {
        onCreate: {
          service: 'CognitoIdentityServiceProvider',
          action: 'adminSetUserPassword',
          parameters: {
            UserPoolId: props.userPool.userPoolId,
            Username: props.email,
            Password: props.password,
            Permanent: true, // Make password permanent
          },
          physicalResourceId: customResources.PhysicalResourceId.of(
            `bootstrap-password-${props.email}`
          ),
        },
        policy: customResources.AwsCustomResourcePolicy.fromSdkCalls({
          resources: customResources.AwsCustomResourcePolicy.ANY_RESOURCE,
        }),
        logGroup: setPasswordLogGroup,
      }
    );

    // Ensure password is set after user creation
    setPermanentPassword.node.addDependency(createUserCustomResource);
  }
}
