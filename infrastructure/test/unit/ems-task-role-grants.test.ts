import { synthStagingApp } from './helpers/synth-staging-app';

jest.mock('aws-cdk-lib/aws-s3-deployment', () =>
  require('./helpers/synth-staging-app').s3DeploymentWithInlineSite()
);

interface Statement {
  Effect: string;
  Action: string | string[];
  Resource: unknown;
}

/**
 * The EMS task role as deployed, read from the real staging app.
 *
 * EMS keeps inbound attachments under `mail-attachments/` in the inbound bucket and the retention
 * job deletes them. The grant once lived only in InboundEmailStack behind a prop bin never set, so
 * a stack test passed while production answered AccessDenied and the mails landed in the DLQ.
 * Only the synthesized app shows what the role really holds.
 */
describe('EMS task role grants on the staging app', () => {
  let statements: Statement[];
  let managedPolicies: unknown[];
  let cleanup: () => void;

  beforeAll(() => {
    const synth = synthStagingApp();
    cleanup = synth.cleanup;
    const template = JSON.parse(synth.templates['BATbern-staging-EventManagement.template.json']);
    const taskRoles = Object.entries(template.Resources as Record<string, any>)
      .filter(
        ([, r]) =>
          r.Type === 'AWS::IAM::Role' &&
          JSON.stringify(r.Properties.AssumeRolePolicyDocument).includes('ecs-tasks.amazonaws.com')
      )
      .map(([id]) => id);
    const resources = template.Resources as Record<string, any>;
    const attached = Object.values(resources)
      .filter(
        (r) =>
          r.Type === 'AWS::IAM::Policy' &&
          ((r.Properties.Roles ?? []) as unknown[]).some((ref) =>
            taskRoles.includes((ref as { Ref?: string }).Ref ?? '')
          )
      )
      .map((r) => r.Properties.PolicyDocument);
    const inline = taskRoles.flatMap((id) =>
      ((resources[id].Properties.Policies ?? []) as { PolicyDocument: unknown }[]).map(
        (p) => p.PolicyDocument
      )
    );
    statements = [...attached, ...inline].flatMap(
      (doc) => (doc as { Statement: Statement[] }).Statement
    );
    managedPolicies = taskRoles.flatMap((id) => resources[id].Properties.ManagedPolicyArns ?? []);
  }, 300000);

  afterAll(() => cleanup?.());

  const actionsOn = (resourcePart: string) =>
    statements
      .filter((s) => s.Effect === 'Allow' && JSON.stringify(s.Resource).includes(resourcePart))
      .flatMap((s) => [s.Action].flat());

  test('should_readTheInboundBucket_when_stagingAppBuilt', () => {
    // Positive control: the role's policies were found and the inbound bucket is referenced.
    expect(actionsOn('InboundEmail')).toEqual(expect.arrayContaining(['s3:GetObject*']));
  });

  test('should_putAndDeleteAttachments_when_stagingAppBuilt', () => {
    expect(actionsOn('/mail-attachments/*')).toEqual(
      expect.arrayContaining(['s3:PutObject', 's3:DeleteObject*'])
    );
  });

  test('should_writeTheInboundBucketOnlyUnderMailAttachments_when_stagingAppBuilt', () => {
    const writes = statements.filter(
      (s) =>
        s.Effect === 'Allow' &&
        JSON.stringify(s.Resource).includes('InboundEmail') &&
        [s.Action].flat().some((a) => /^s3:(Put|Delete)Object/.test(a))
    );
    expect(writes.length).toBeGreaterThan(0);
    for (const s of writes) {
      for (const resource of [s.Resource].flat()) {
        expect(JSON.stringify(resource)).toContain('/mail-attachments/*');
      }
    }
  });

  test('should_grantNoEventBridgeAction_when_stagingAppBuilt', () => {
    // Auto-publish is a @Scheduled job; EMS calls no EventBridge API (Story 17.2, ADR-016).
    const allActions = statements.flatMap((s) => [s.Action].flat());
    expect(allActions.length).toBeGreaterThan(0);
    expect(allActions.filter((a) => a.startsWith('events:'))).toEqual([]);
    expect(JSON.stringify(managedPolicies)).not.toMatch(/EventBridge|CloudWatchEvents/);
  });
});
