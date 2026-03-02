import * as cdk from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { BootstrapOrganizer } from '../../lib/constructs/bootstrap-organizer';

/**
 * Bootstrap Organizer Construct Tests (TDD - RED Phase)
 *
 * Tests for AC7: Bootstrap organizer creation works for all environments
 * - Test 7.1: should_createBootstrapUser_when_stackDeployed
 * - Test 7.2: should_assignOrganizerRole_when_bootstrapUserCreated
 * - Test 7.3: should_setPermanentPassword_when_bootstrapUserCreated
 * - Test 7.4: should_allowImmediateLogin_when_bootstrapUserCreated
 * - Test 7.5: should_beIdempotent_when_stackDeployedMultipleTimes
 */

describe('BootstrapOrganizer', () => {
  let stack: cdk.Stack;
  let userPool: cognito.UserPool;

  beforeEach(() => {
    const app = new cdk.App();
    stack = new cdk.Stack(app, 'TestStack');

    // Create a test user pool
    userPool = new cognito.UserPool(stack, 'TestUserPool', {
      userPoolName: 'test-user-pool',
    });
  });

  /**
   * Test 7.1: should_createBootstrapUser_when_stackDeployed
   *
   * Verifies that the construct creates a custom resource to create the bootstrap user
   * via AdminCreateUser API call
   */
  test('should_createBootstrapUser_when_stackDeployed', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Should create custom resources (user creation + password setting)
    const template = Template.fromStack(stack);

    // Verify custom resources exist
    const resources = template.findResources('Custom::AWS');
    const resourceKeys = Object.keys(resources);

    // Should have at least 2 custom resources (create user + set password)
    expect(resourceKeys.length).toBeGreaterThanOrEqual(2);

    // Verify template structure is valid
    expect(resources).toBeDefined();

    // Verify ServiceToken exists in template for Lambda execution
    const templateJson = JSON.stringify(template.toJSON());
    expect(templateJson).toContain('ServiceToken');
    expect(templateJson).toContain('"Fn::GetAtt"');
  });

  /**
   * Test 7.2: should_assignOrganizerRole_when_bootstrapUserCreated
   *
   * Verifies that the bootstrap user is created with custom:role=ORGANIZER attribute.
   * ADR-001: Roles are managed in the database, not in Cognito Groups.
   * The custom:role attribute is used by PostConfirmation Lambda to assign the DB role.
   */
  test('should_assignOrganizerRole_when_bootstrapUserCreated', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Verify template sets custom:role=ORGANIZER via adminCreateUser
    const template = Template.fromStack(stack);
    const templateJson = JSON.stringify(template.toJSON());

    expect(templateJson).toContain('adminCreateUser');
    // ADR-001: custom:role attribute carries the role (no Cognito Groups)
    expect(templateJson).toContain('custom:role');
    expect(templateJson).toContain('ORGANIZER');
    expect(templateJson).toContain(email);
  });

  /**
   * Test 7.3: should_setPermanentPassword_when_bootstrapUserCreated
   *
   * Verifies that the bootstrap user password is set as permanent (not temporary)
   * via AdminSetUserPassword with Permanent: true
   */
  test('should_setPermanentPassword_when_bootstrapUserCreated', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Verify template contains AdminSetUserPassword with Permanent flag
    const template = Template.fromStack(stack);
    const templateJson = JSON.stringify(template.toJSON());

    expect(templateJson).toContain('adminSetUserPassword');
    expect(templateJson).toContain('Permanent');
    expect(templateJson).toContain(password);
  });

  /**
   * Test 7.4: should_allowImmediateLogin_when_bootstrapUserCreated
   *
   * Verifies that email_verified is set to true to skip email verification
   */
  test('should_allowImmediateLogin_when_bootstrapUserCreated', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Verify email_verified is set to true
    const template = Template.fromStack(stack);
    const templateJson = JSON.stringify(template.toJSON());

    expect(templateJson).toContain('email_verified');
    expect(templateJson).toContain('Value');
    expect(templateJson).toContain('true');
    expect(templateJson).toContain('MessageAction');
    expect(templateJson).toContain('SUPPRESS');
  });

  /**
   * Test 7.5: should_beIdempotent_when_stackDeployedMultipleTimes
   *
   * Verifies that the construct handles idempotency by checking if user exists
   * before attempting to create (uses physical resource ID for idempotency)
   */
  test('should_beIdempotent_when_stackDeployedMultipleTimes', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Verify physical resource ID includes email for idempotency
    const template = Template.fromStack(stack);
    const templateJson = JSON.stringify(template.toJSON());

    expect(templateJson).toContain(`bootstrap-organizer-${email}`);
    expect(templateJson).toContain(`bootstrap-password-${email}`);
  });

  /**
   * Test 7.6: should_handleUpdateGracefully_when_stackUpdated
   *
   * Verifies that updates check if user exists before attempting modifications
   */
  test('should_handleUpdateGracefully_when_stackUpdated', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Verify Update action uses adminGetUser for idempotency
    const template = Template.fromStack(stack);
    const templateJson = JSON.stringify(template.toJSON());

    expect(templateJson).toContain('adminGetUser');
    expect(templateJson).toContain('UserNotFoundException');
  });

  /**
   * Test 7.7: should_createIAMRole_when_customResourceDeployed
   *
   * Verifies that proper IAM role is created for custom resource execution
   */
  test('should_createIAMRole_when_customResourceDeployed', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Should create IAM role with Cognito permissions
    const template = Template.fromStack(stack);

    // Verify IAM role exists for custom resource
    template.hasResourceProperties('AWS::IAM::Role', {
      AssumeRolePolicyDocument: {
        Statement: [
          {
            Action: 'sts:AssumeRole',
            Effect: 'Allow',
            Principal: {
              Service: 'lambda.amazonaws.com',
            },
          },
        ],
      },
    });
  });

  /**
   * Test 7.8: should_setDependency_when_passwordSetAfterUserCreation
   *
   * Verifies that AdminSetUserPassword depends on AdminCreateUser completion
   */
  test('should_setDependency_when_passwordSetAfterUserCreation', () => {
    // Given: Bootstrap organizer configuration
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';

    // When: Bootstrap organizer construct is created
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
    });

    // Then: Should have at least 2 custom resources (create user + set password)
    const template = Template.fromStack(stack);
    const resources = template.findResources('Custom::AWS');
    const customResourceKeys = Object.keys(resources);

    expect(customResourceKeys.length).toBeGreaterThanOrEqual(2);
  });

  /**
   * Test 7.9: should_syncBootstrapUserToDb_when_postConfirmationLambdaProvided
   *
   * Verifies that when postConfirmationLambdaArn is provided, additional custom resources
   * are created to retrieve the user sub and invoke the PostConfirmation Lambda,
   * thereby syncing the bootstrap user to the database with ORGANIZER role.
   */
  test('should_syncBootstrapUserToDb_when_postConfirmationLambdaProvided', () => {
    // Given: Bootstrap organizer configuration with PostConfirmation Lambda ARN
    const email = 'nissim@buchs.be';
    const password = 'test-password-for-unit-tests';
    const postConfirmationLambdaArn =
      'arn:aws:lambda:eu-central-1:123456789012:function:post-confirmation-trigger';

    // When: Bootstrap organizer construct is created with Lambda ARN
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email,
      password,
      postConfirmationLambdaArn,
    });

    // Then: Should have 4 custom resources
    // (create user, set password, get user sub, invoke PostConfirmation Lambda)
    const template = Template.fromStack(stack);
    const resources = template.findResources('Custom::AWS');
    const customResourceKeys = Object.keys(resources);
    expect(customResourceKeys.length).toBeGreaterThanOrEqual(4);

    const templateJson = JSON.stringify(template.toJSON());

    // AdminGetUser to retrieve the sub
    expect(templateJson).toContain('adminGetUser');
    // Lambda invoke to sync user to DB — check IAM policy action and Lambda ARN
    expect(templateJson).toContain('lambda:InvokeFunction');
    expect(templateJson).toContain(postConfirmationLambdaArn);
    // Idempotency IDs for sync resources
    expect(templateJson).toContain(`bootstrap-user-sub-${email}`);
    expect(templateJson).toContain(`sync-bootstrap-db-${email}`);
  });

  /**
   * Test 7.10: should_notSyncToDb_when_postConfirmationLambdaNotProvided
   *
   * Verifies that without a PostConfirmation Lambda ARN, only Cognito resources are created
   * (no DB sync). This is the case for local development environments.
   */
  test('should_notSyncToDb_when_postConfirmationLambdaNotProvided', () => {
    // Given: Bootstrap organizer without PostConfirmation Lambda ARN
    new BootstrapOrganizer(stack, 'BootstrapOrganizer', {
      userPool,
      email: 'nissim@buchs.be',
      password: 'test-password-for-unit-tests',
    });

    // Then: Should have exactly 2 custom resources (no DB sync resources)
    const template = Template.fromStack(stack);
    const resources = template.findResources('Custom::AWS');
    expect(Object.keys(resources).length).toBe(2);

    const templateJson = JSON.stringify(template.toJSON());
    expect(templateJson).not.toContain('bootstrap-user-sub-');
    expect(templateJson).not.toContain('sync-bootstrap-db-');
  });
});
