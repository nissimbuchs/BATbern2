/**
 * CICD stack tests — GitHub Actions IAM role permissions.
 *
 * These tests document and enforce EVERY AWS CLI action called by the deploy
 * workflows (deploy-staging.yml, deploy-code-staging.yml, update-ecs-task.sh).
 * If a new workflow step calls an AWS API that isn't covered here, add it to
 * BOTH the WorkflowRuntimePolicy in cicd-stack.ts AND a test below.
 *
 * The pre-push hook enforces this automatically: when deploy-staging.yml or
 * cicd-stack.ts changes, it greps the workflow for `aws` calls and fails if
 * any found action is missing from this test file.
 *
 * How to find new actions manually:
 *   grep -oP 'aws \K[a-z0-9-]+ [a-z0-9-]+' .github/workflows/deploy-staging.yml | sort -u
 *   Map each CLI sub-command to its IAM action and add a test below if missing.
 *
 * Policy structure (two explicit ManagedPolicies, no invisible inline overflow):
 *   WorkflowRuntimePolicy  — actions called directly by workflow YAML steps
 *   CdkDeploymentPolicy    — everything `cdk deploy` needs to provision infrastructure
 *
 * Incident reference: 2026-05-08 — ecs:ListTaskDefinitions missing, discovered
 * only at runtime when the post-deploy simulate-principal-policy step ran.
 */

import { App } from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';

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

/**
 * Returns true if a ManagedPolicy with the given ManagedPolicyName exists.
 * Accepts both exact strings and CDK Join tokens (which appear when the name
 * is constructed with account/region tokens).
 */
function hasManagedPolicy(template: Template, nameSubstring: string): boolean {
  const policies = template.findResources('AWS::IAM::ManagedPolicy');
  for (const resource of Object.values(policies)) {
    const name = (resource as { Properties?: { ManagedPolicyName?: unknown } })
      .Properties?.ManagedPolicyName;
    // The name may be a plain string or a CloudFormation Join/Sub token
    const nameStr = JSON.stringify(name ?? '');
    if (nameStr.includes(nameSubstring)) return true;
  }
  return false;
}

describe('CICDStack — GitHub Actions role permissions', () => {
  let template: Template;

  beforeAll(() => {
    template = buildTemplate();
  });

  // ── Policy structure ──────────────────────────────────────────────────────
  // Both policies must be explicit ManagedPolicies, not invisible inline overflow.

  test('should_createWorkflowRuntimePolicy_as_namedManagedPolicy', () => {
    expect(hasManagedPolicy(template, 'github-workflow-runtime')).toBe(true);
  });

  test('should_createCdkDeploymentPolicy_as_namedManagedPolicy', () => {
    expect(hasManagedPolicy(template, 'github-cdk-deployment')).toBe(true);
  });

  test('should_createCdkResourcesPolicy_as_namedManagedPolicy', () => {
    expect(hasManagedPolicy(template, 'github-cdk-resources')).toBe(true);
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

  // ── Policy separation ─────────────────────────────────────────────────────
  // Runtime actions must live in WorkflowRuntimePolicy, not bleed into the CDK policy.

  test('should_placeWorkflowRuntimeActions_in_WorkflowRuntimePolicy', () => {
    const policies = template.findResources('AWS::IAM::ManagedPolicy');

    const runtimePolicy = Object.values(policies).find(p => {
      const nameStr = JSON.stringify(
        (p as { Properties?: { ManagedPolicyName?: unknown } }).Properties?.ManagedPolicyName ?? '',
      );
      return nameStr.includes('github-workflow-runtime');
    });

    expect(runtimePolicy).toBeDefined();

    // Spot-check: a representative runtime action must appear in this policy
    const statements: unknown[] =
      (runtimePolicy as { Properties?: { PolicyDocument?: { Statement?: unknown[] } } })
        .Properties?.PolicyDocument?.Statement ?? [];

    const allActions = statements.flatMap(stmt => {
      const s = stmt as { Action?: unknown };
      return Array.isArray(s.Action)
        ? (s.Action as unknown[]).filter((a): a is string => typeof a === 'string')
        : typeof s.Action === 'string'
          ? [s.Action]
          : [];
    });

    expect(allActions).toContain('ecs:ListTaskDefinitions');
    expect(allActions).toContain('iam:SimulatePrincipalPolicy');
    expect(allActions).toContain('cognito-idp:InitiateAuth');
  });

  // Prove that CDK deployment actions (e.g. cloudformation:CreateStack) are NOT
  // inside the runtime policy — they should stay in CdkDeploymentPolicy.
  test('should_notPlaceCdkDeployActions_in_WorkflowRuntimePolicy', () => {
    const policies = template.findResources('AWS::IAM::ManagedPolicy');

    const runtimePolicy = Object.values(policies).find(p => {
      const nameStr = JSON.stringify(
        (p as { Properties?: { ManagedPolicyName?: unknown } }).Properties?.ManagedPolicyName ?? '',
      );
      return nameStr.includes('github-workflow-runtime');
    });

    expect(runtimePolicy).toBeDefined();

    const statements: unknown[] =
      (runtimePolicy as { Properties?: { PolicyDocument?: { Statement?: unknown[] } } })
        .Properties?.PolicyDocument?.Statement ?? [];

    const runtimeActions = new Set(
      statements.flatMap(stmt => {
        const s = stmt as { Action?: unknown };
        return Array.isArray(s.Action)
          ? (s.Action as unknown[]).filter((a): a is string => typeof a === 'string')
          : typeof s.Action === 'string'
            ? [s.Action]
            : [];
      }),
    );

    // These are CDK-only actions — they must not appear in the runtime policy
    expect(runtimeActions.has('cloudformation:CreateStack')).toBe(false);
    expect(runtimeActions.has('iam:CreateRole')).toBe(false);
    expect(runtimeActions.has('ec2:CreateVpc')).toBe(false);
  });
});
