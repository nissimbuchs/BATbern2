import { synthStagingApp } from './helpers/synth-staging-app';

jest.mock('aws-cdk-lib/aws-s3-deployment', () =>
  require('./helpers/synth-staging-app').s3DeploymentWithInlineSite()
);

interface ContainerSecret {
  Name: string;
  ValueFrom: unknown;
}

/**
 * Agent runtime story 3: the Anthropic API key of agent-service's model adapter, read from the real
 * staging app (bin wiring included). CDK creates the secret with the placeholder `none` (adapter
 * off, the service still starts); the owner writes the real key with
 * scripts/deploy/put-anthropic-api-key.sh. The key reaches agent-service only, as
 * AGENT_ANTHROPIC_API_KEY. Found by the logical-id fragment of its SecretsStack resource, which
 * appears in every cross-stack reference to it.
 */
describe('Anthropic API key on the staging app', () => {
  const SECRETS = 'BATbern-staging-Secrets.template.json';
  const AGENT = 'BATbern-staging-AgentService.template.json';
  const KEY = 'AgentAnthropicApiKey';

  let templates: Record<string, string>;
  let cleanup: () => void;

  beforeAll(() => {
    const synth = synthStagingApp();
    templates = synth.templates;
    cleanup = synth.cleanup;
  }, 300000);

  afterAll(() => cleanup?.());

  const containerSecrets = (file: string): Record<string, string> => {
    const resources = JSON.parse(templates[file]).Resources as Record<string, any>;
    const out: Record<string, string> = {};
    Object.values(resources)
      .filter((r) => r.Type === 'AWS::ECS::TaskDefinition')
      .flatMap((r) => r.Properties.ContainerDefinitions as { Secrets?: ContainerSecret[] }[])
      .flatMap((c) => c.Secrets ?? [])
      .forEach((s) => {
        out[s.Name] = JSON.stringify(s.ValueFrom);
      });
    return out;
  };

  const referencedBy = (fragment: string) =>
    Object.keys(templates)
      .filter((file) => file !== SECRETS && templates[file].includes(fragment))
      .sort();

  test('should_injectKey_as_AGENT_ANTHROPIC_API_KEY_when_agentServiceTaskDefined', () => {
    // Positive control for the "agent-service only" check below
    expect(containerSecrets(AGENT).AGENT_ANTHROPIC_API_KEY).toContain(KEY);
  });

  test('should_referenceKey_when_agentServiceOnly', () => {
    expect(referencedBy(KEY)).toEqual([AGENT]);
  });

  test('should_notInjectKey_when_anyOtherServiceTaskDefined', () => {
    for (const file of Object.keys(templates).filter((f) => f !== AGENT && f !== SECRETS)) {
      expect(templates[file]).not.toContain('AGENT_ANTHROPIC_API_KEY');
    }
  });

  /*
   * Pins the complete deployed Properties of the key secret. Any change to them makes
   * CloudFormation write `none` back over the real key on the next deploy and silently switches the
   * adapter off in production. If you change one on purpose: after deploying it, re-run
   * scripts/deploy/put-anthropic-api-key.sh --force (docs/operations/runbooks.md -> "Anthropic API key").
   */
  test('should_keepKeyResourceProperties_when_stagingAppSynthesized', () => {
    const resources = JSON.parse(templates[SECRETS]).Resources as Record<
      string,
      { Type: string; Properties: unknown }
    >;
    const entry = Object.entries(resources).find(([id]) => id.startsWith(KEY));
    const hint =
      'Anthropic key resource properties changed: this deploy resets the key to `none`. ' +
      'After deploying, re-run scripts/deploy/put-anthropic-api-key.sh --force (runbook "Anthropic API key").';
    expect({ hint, type: entry?.[1].Type, props: entry?.[1].Properties }).toEqual({
      hint,
      type: 'AWS::SecretsManager::Secret',
      props: {
        Description:
          'Anthropic API key of the agent service model adapter; written by scripts/deploy/put-anthropic-api-key.sh',
        Name: 'batbern/staging/agent/anthropic-api-key',
        SecretString: 'none',
        Tags: Object.entries({
          Component: 'Security',
          Environment: 'staging',
          ManagedBy: 'CDK',
          Project: 'BATbern',
        }).map(([Key, Value]) => ({ Key, Value })),
      },
    });
  });
});
