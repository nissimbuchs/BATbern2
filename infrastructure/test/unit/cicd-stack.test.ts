/**
 * CICD stack tests — GitHub Actions IAM role permissions.
 *
 * These tests document and enforce EVERY AWS CLI action called by the deploy
 * workflows (deploy-staging.yml, deploy-code-staging.yml, update-ecs-task.sh).
 * If a new workflow step calls an AWS API that isn't covered here, add it to
 * both the test AND the CICDStack policy — failing this test is the pre-deploy
 * signal that a permission is missing, not a runtime AccessDeniedException.
 *
 * How to keep this in sync:
 *   grep -oP 'aws \K[a-z0-9-]+ [a-z0-9-]+' .github/workflows/deploy-staging.yml | sort -u
 *   Map each CLI sub-command to its IAM action and add a test below if missing.
 *
 * Implementation note — CDK splits the role's policy across two resource types:
 *   AWS::IAM::Policy       (inline, ~6 KB limit)
 *   AWS::IAM::ManagedPolicy (overflow once the inline limit is reached)
 * Both are attached to the same role. The hasAction() helper below checks both
 * so tests remain valid regardless of which resource CDK chooses for overflow.
 *
 * Incident reference: 2026-05-08 — ecs:ListTaskDefinitions missing, discovered
 * only at runtime when the post-deploy simulate-principal-policy step ran.
 */

import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';

import { stagingConfig } from '../../lib/config/staging-config';
import { CICDStack } from '../../lib/stacks/cicd-stack';

function buildTemplate(): Template {
  const app = new App();
  const stack = new CICDStack(app, 'TestCICD', {
    config: stagingConfig,
    githubRepository: 'nissimbuchs/BATbern2',
    env: { account: '123456789012', region: 'eu-central-1' },
  });
  return Template.fromStack(stack);
}

/**
 * Returns true if the given IAM action appears in any policy statement across
 * both AWS::IAM::Policy and AWS::IAM::ManagedPolicy resources in the template.
 * Handles both array and single-string Action values.
 */
function hasAction(template: Template, action: string): boolean {
  for (const resourceType of ['AWS::IAM::Policy', 'AWS::IAM::ManagedPolicy']) {
    const resources = template.findResources(resourceType);
    for (const resource of Object.values(resources)) {
      const statements: unknown[] =
        (resource as { Properties?: { PolicyDocument?: { Statement?: unknown[] } } })
          .Properties?.PolicyDocument?.Statement ?? [];
      for (const stmt of statements) {
        const s = stmt as { Action?: unknown };
        const actions = Array.isArray(s.Action)
          ? (s.Action as unknown[]).filter((a): a is string => typeof a === 'string')
          : typeof s.Action === 'string'
            ? [s.Action]
            : [];
        if (actions.includes(action)) return true;
      }
    }
  }
  return false;
}

describe('CICDStack — GitHub Actions role permissions', () => {
  let template: Template;

  beforeAll(() => {
    template = buildTemplate();
  });

  // ── ECS ──────────────────────────────────────────────────────────────────
  // Used by: deploy-staging.yml (stabilize, cleanup) + update-ecs-task.sh (fast-path)

  test('should_grantEcsDescribeServices_for_stabilizeAndCleanup', () => {
    expect(hasAction(template, 'ecs:DescribeServices')).toBe(true);
  });

  test('should_grantEcsDescribeClusters_for_stabilizeCheck', () => {
    expect(hasAction(template, 'ecs:DescribeClusters')).toBe(true);
  });

  test('should_grantEcsDescribeTaskDefinition_for_iamSimulationGate', () => {
    expect(hasAction(template, 'ecs:DescribeTaskDefinition')).toBe(true);
  });

  test('should_grantEcsListServices_for_fastPathDeployment', () => {
    expect(hasAction(template, 'ecs:ListServices')).toBe(true);
  });

  // Regression: missing on 2026-05-08 — caused AccessDeniedException when the
  // simulate-principal-policy step tried to resolve the task role ARN.
  test('should_grantEcsListTaskDefinitions_for_iamSimulationGate', () => {
    expect(hasAction(template, 'ecs:ListTaskDefinitions')).toBe(true);
  });

  test('should_grantEcsRegisterTaskDefinition_for_fastPathDeployment', () => {
    expect(hasAction(template, 'ecs:RegisterTaskDefinition')).toBe(true);
  });

  test('should_grantEcsUpdateService_for_fastPathDeployment', () => {
    expect(hasAction(template, 'ecs:UpdateService')).toBe(true);
  });

  // ── IAM ──────────────────────────────────────────────────────────────────
  // Used by: deploy-staging.yml simulate-principal-policy step

  test('should_grantIamSimulatePrincipalPolicy_for_postDeployPermissionGate', () => {
    expect(hasAction(template, 'iam:SimulatePrincipalPolicy')).toBe(true);
  });

  // ── CloudFormation ───────────────────────────────────────────────────────
  // Used by: deploy-staging.yml (CDK deploy + stuck-stack cleanup steps)

  test('should_grantCloudFormationDescribeStacks_for_deployAndCleanup', () => {
    expect(hasAction(template, 'cloudformation:DescribeStacks')).toBe(true);
  });

  test('should_grantCloudFormationCancelUpdateStack_for_stuckStackRecovery', () => {
    expect(hasAction(template, 'cloudformation:CancelUpdateStack')).toBe(true);
  });

  test('should_grantCloudFormationContinueUpdateRollback_for_stuckStackRecovery', () => {
    expect(hasAction(template, 'cloudformation:ContinueUpdateRollback')).toBe(true);
  });

  // ── ECR ──────────────────────────────────────────────────────────────────
  // Used by: deploy-staging.yml ECR image validation step

  test('should_grantEcrDescribeImages_for_preDeployImageValidation', () => {
    expect(hasAction(template, 'ecr:DescribeImages')).toBe(true);
  });

  // ── RDS ──────────────────────────────────────────────────────────────────
  // Used by: deploy-staging.yml pre-deploy database backup step

  test('should_grantRdsCreateDbSnapshot_for_preDeployBackup', () => {
    expect(hasAction(template, 'rds:CreateDBSnapshot')).toBe(true);
  });

  test('should_grantRdsDescribeDbInstances_for_preDeployBackup', () => {
    expect(hasAction(template, 'rds:DescribeDBInstances')).toBe(true);
  });

  // ── Cognito ──────────────────────────────────────────────────────────────
  // Used by: deploy-staging.yml authenticate test users step (cognito-idp initiate-auth)

  test('should_grantCognitoInitiateAuth_for_testUserAuthentication', () => {
    expect(hasAction(template, 'cognito-idp:InitiateAuth')).toBe(true);
  });
});
