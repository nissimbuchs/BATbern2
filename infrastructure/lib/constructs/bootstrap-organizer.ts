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
 * - Assigns ORGANIZER role via custom:role attribute
 * - Sets email_verified to true for immediate login
 * - Idempotent - safe to deploy multiple times
 * - If postConfirmationLambdaArn is provided, syncs the user to the database
 *   with ORGANIZER role (AdminCreateUser does NOT fire the PostConfirmation trigger)
 */

export interface BootstrapOrganizerProps {
  userPool: cognito.IUserPool;
  email: string;
  password: string;
  /**
   * ARN of the PostConfirmation Lambda trigger.
   * When provided, the bootstrap user is synced to the database with ORGANIZER role
   * by invoking the Lambda with a synthetic PostConfirmation event.
   * Required for environments with a VPC-backed database (staging, production).
   */
  postConfirmationLambdaArn?: string;
}

export class BootstrapOrganizer extends Construct {
  constructor(scope: Construct, id: string, props: BootstrapOrganizerProps) {
    super(scope, id);

    // Create log groups for custom resources
    // Note: no hardcoded logGroupName — CDK generates a unique name per stack to avoid
    // cross-environment collisions and orphaned log group conflicts on rollback.
    const createUserLogGroup = new logs.LogGroup(this, 'CreateUserLogGroup', {
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const setPasswordLogGroup = new logs.LogGroup(this, 'SetPasswordLogGroup', {
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
                Value: 'ORGANIZER',
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

    // Sync bootstrap user to database with ORGANIZER role.
    // AdminCreateUser does NOT fire the PostConfirmation Cognito trigger, so the user
    // never reaches the DB through the normal flow. We invoke the PostConfirmation Lambda
    // manually with a synthetic event to create the user_profiles + role_assignments rows.
    if (props.postConfirmationLambdaArn) {
      const getUserSubLogGroup = new logs.LogGroup(this, 'GetUserSubLogGroup', {
        retention: logs.RetentionDays.ONE_WEEK,
        removalPolicy: cdk.RemovalPolicy.DESTROY,
      });

      // Retrieve the bootstrap user's Cognito sub (UUID) so we can pass it in the
      // synthetic PostConfirmation event. AdminGetUser always returns 'sub' as the
      // first attribute in the UserAttributes list.
      const getUserSubResource = new customResources.AwsCustomResource(
        this,
        'GetBootstrapUserSub',
        {
          onCreate: {
            service: 'CognitoIdentityServiceProvider',
            action: 'adminGetUser',
            parameters: {
              UserPoolId: props.userPool.userPoolId,
              Username: props.email,
            },
            physicalResourceId: customResources.PhysicalResourceId.of(
              `bootstrap-user-sub-${props.email}`
            ),
          },
          onUpdate: {
            service: 'CognitoIdentityServiceProvider',
            action: 'adminGetUser',
            parameters: {
              UserPoolId: props.userPool.userPoolId,
              Username: props.email,
            },
            physicalResourceId: customResources.PhysicalResourceId.of(
              `bootstrap-user-sub-${props.email}`
            ),
          },
          policy: customResources.AwsCustomResourcePolicy.fromSdkCalls({
            resources: customResources.AwsCustomResourcePolicy.ANY_RESOURCE,
          }),
          logGroup: getUserSubLogGroup,
        }
      );
      getUserSubResource.node.addDependency(setPermanentPassword);

      // sub is the first attribute in Cognito's AdminGetUser response
      const userSub = getUserSubResource.getResponseField('UserAttributes.0.Value');

      const syncUserLogGroup = new logs.LogGroup(this, 'SyncUserToDbLogGroup', {
        retention: logs.RetentionDays.ONE_WEEK,
        removalPolicy: cdk.RemovalPolicy.DESTROY,
      });

      // Build the synthetic PostConfirmation event JSON.
      // Fn.join is used so the CloudFormation-resolved sub token is embedded correctly.
      const syntheticEventPayload = cdk.Fn.join('', [
        '{"version":"1","triggerSource":"PostConfirmation_ConfirmSignUp","userPoolId":"',
        props.userPool.userPoolId,
        '","userName":"',
        props.email,
        '","callerContext":{"awsSdkVersion":"bootstrap","clientId":"bootstrap"}',
        ',"request":{"userAttributes":{"sub":"',
        userSub,
        '","email":"',
        props.email,
        '","email_verified":"true","custom:role":"ORGANIZER"}},"response":{}}',
      ]);

      // Invoke the PostConfirmation Lambda to create user_profiles + role_assignments rows.
      // The Lambda runs inside the VPC; this is a control-plane call and does not require
      // the custom resource to be in the VPC.
      const syncUserToDbResource = new customResources.AwsCustomResource(
        this,
        'SyncBootstrapUserToDb',
        {
          onCreate: {
            service: 'Lambda',
            action: 'invoke',
            parameters: {
              FunctionName: props.postConfirmationLambdaArn,
              Payload: syntheticEventPayload,
            },
            physicalResourceId: customResources.PhysicalResourceId.of(
              `sync-bootstrap-db-${props.email}`
            ),
          },
          policy: customResources.AwsCustomResourcePolicy.fromStatements([
            new cdk.aws_iam.PolicyStatement({
              effect: cdk.aws_iam.Effect.ALLOW,
              actions: ['lambda:InvokeFunction'],
              resources: [props.postConfirmationLambdaArn],
            }),
          ]),
          logGroup: syncUserLogGroup,
        }
      );
      syncUserToDbResource.node.addDependency(getUserSubResource);
    }
  }
}
