import { synthStagingApp } from './helpers/synth-staging-app';

jest.mock('aws-cdk-lib/aws-s3-deployment', () =>
  require('./helpers/synth-staging-app').s3DeploymentWithInlineSite()
);

interface ContainerSecret {
  Name: string;
  ValueFrom: unknown;
}

/**
 * Agentic interaction 0.3: who gets the agent signing key and the caller secrets, read from the
 * real staging app (bin wiring included).
 *
 * The private key reaches CUMS only; the agent-service caller secret reaches CUMS and agent-service
 * (story 4 of the agent runtime, as AGENT_CUMS_CALLER_SECRET); the EMS caller secret reaches CUMS and
 * EMS; the public key (an SSM parameter) reaches all four core services. Each value is
 * found by the logical-id fragment of its SecretsStack resource, which appears in every
 * cross-stack reference to it.
 */
describe('Agent signing key and caller secrets on the staging app', () => {
  const SECRETS = 'BATbern-staging-Secrets.template.json';
  const SERVICES = {
    cums: 'BATbern-staging-CompanyManagement.template.json',
    ems: 'BATbern-staging-EventManagement.template.json',
    pcs: 'BATbern-staging-PartnerCoordination.template.json',
    gateway: 'BATbern-staging-ApiGatewayService.template.json',
  } as const;
  const AGENT_SERVICE = 'BATbern-staging-AgentService.template.json';
  const PRIVATE_KEY = 'AgentSigningPrivateKey';
  const PUBLIC_KEY = 'AgentJwtPublicKey';
  const EMS_CALLER = 'AgentEmsCallerSecret';
  const AGENT_SERVICE_CALLER = 'AgentServiceCallerSecret';

  let templates: Record<string, string>;
  let cleanup: () => void;

  beforeAll(() => {
    const synth = synthStagingApp();
    templates = synth.templates;
    cleanup = synth.cleanup;
  }, 300000);

  afterAll(() => cleanup?.());

  /** Container secrets of every task definition in the template, by env name. */
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

  /** Template files other than SecretsStack that mention the fragment anywhere. */
  const referencedBy = (fragment: string) =>
    Object.keys(templates)
      .filter((file) => file !== SECRETS && templates[file].includes(fragment))
      .sort();

  test('should_createAllFourResources_when_secretsStackSynthesized', () => {
    for (const fragment of [PRIVATE_KEY, PUBLIC_KEY, EMS_CALLER, AGENT_SERVICE_CALLER]) {
      expect(templates[SECRETS]).toContain(fragment);
    }
  });

  test('should_injectPrivateKeyAndBothCallerSecrets_when_cumsTaskDefined', () => {
    // Positive control for the absence checks below: CUMS references every one of them.
    const secrets = containerSecrets(SERVICES.cums);
    expect(secrets.AGENT_JWT_PRIVATE_KEY).toContain(PRIVATE_KEY);
    expect(secrets.AGENT_JWT_PUBLIC_KEY).toContain(PUBLIC_KEY);
    expect(secrets.AGENT_CALLERS_EMS_SECRET).toContain(EMS_CALLER);
    expect(secrets.AGENT_CALLERS_AGENT_SERVICE_SECRET).toContain(AGENT_SERVICE_CALLER);
  });

  test('should_injectEmsCallerSecret_when_emsTaskDefined', () => {
    const secrets = containerSecrets(SERVICES.ems);
    expect(secrets.AGENT_CALLERS_EMS_SECRET).toContain(EMS_CALLER);
    expect(secrets.AGENT_JWT_PRIVATE_KEY).toBeUndefined();
    expect(secrets.AGENT_CALLERS_AGENT_SERVICE_SECRET).toBeUndefined();
  });

  test('should_referencePrivateKey_when_cumsOnly', () => {
    expect(referencedBy(PRIVATE_KEY)).toEqual([SERVICES.cums]);
  });

  test('should_referenceAgentServiceCallerSecret_when_cumsOrAgentService', () => {
    expect(referencedBy(AGENT_SERVICE_CALLER)).toEqual([AGENT_SERVICE, SERVICES.cums].sort());
  });

  test('should_injectAgentServiceCallerSecret_as_AGENT_CUMS_CALLER_SECRET_when_agentServiceTaskDefined', () => {
    const secrets = containerSecrets(AGENT_SERVICE);
    expect(secrets.AGENT_CUMS_CALLER_SECRET).toContain(AGENT_SERVICE_CALLER);
    // Its own name, never CUMS's, and no other agent secret
    expect(secrets.AGENT_CALLERS_AGENT_SERVICE_SECRET).toBeUndefined();
    expect(secrets.AGENT_CALLERS_EMS_SECRET).toBeUndefined();
    expect(secrets.AGENT_JWT_PRIVATE_KEY).toBeUndefined();
  });

  test('should_referenceEmsCallerSecret_when_cumsOrEms', () => {
    expect(referencedBy(EMS_CALLER)).toEqual([SERVICES.cums, SERVICES.ems].sort());
  });

  test.each(Object.entries(SERVICES))(
    'should_injectPublicKeyFromSsm_when_%sTaskDefined',
    (_service, file) => {
      const valueFrom = containerSecrets(file).AGENT_JWT_PUBLIC_KEY;
      expect(valueFrom).toContain(PUBLIC_KEY);
      expect(valueFrom).toContain(':parameter');
    }
  );

  test('should_notInjectPrivateKey_when_emsPcsOrGatewayTaskDefined', () => {
    for (const file of [SERVICES.ems, SERVICES.pcs, SERVICES.gateway]) {
      const secrets = containerSecrets(file);
      // positive control: the task definition's secrets were found
      expect(secrets.AGENT_JWT_PUBLIC_KEY).toBeDefined();
      expect(secrets.AGENT_JWT_PRIVATE_KEY).toBeUndefined();
    }
  });
});
