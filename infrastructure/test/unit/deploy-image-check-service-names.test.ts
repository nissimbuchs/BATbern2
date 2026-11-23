import { spawnSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

/**
 * "Prepare deployment" in deploy-staging.yml checks that the image of every service it deploys
 * exists in ECR. On a selective deploy CHANGED_SERVICES holds the short names the `changes` step
 * emits (agent, company-management, ...), while the check walks ECR repository names
 * (agent-service, company-user-management-service, ...). Until the agent runtime refactor sweep
 * (2026-11-22) it grepped the repository name in the short names, so only api-gateway was ever
 * image-checked on that path. The step now maps each repository to its short name and matches
 * whole words; this test runs that bash against the names the `changes` step really emits.
 */
interface Step {
  name?: string;
  id?: string;
  run?: string;
}
interface Workflow {
  jobs?: Record<string, { steps?: Step[] }>;
}

const workflowFile = path.resolve(__dirname, '../../../.github/workflows/deploy-staging.yml');
const workflow = yaml.load(fs.readFileSync(workflowFile, 'utf8')) as Workflow;
const steps = Object.values(workflow.jobs ?? {}).flatMap((job) => job.steps ?? []);

function stepScript(predicate: (step: Step) => boolean, label: string): string {
  const step = steps.find(predicate);
  if (!step || typeof step.run !== 'string') throw new Error(`step ${label} not found`);
  return step.run;
}

const prepareScript = stepScript((s) => s.name === 'Prepare deployment', 'Prepare deployment');
const changesScript = stepScript((s) => s.id === 'changes', 'changes');

/** The two bash functions of "Prepare deployment", from `service_short_name() {` to the end of `service_selected`. */
function mappingFunctions(): string {
  const match = prepareScript.match(
    /^\s*service_short_name\(\) \{[\s\S]*?^\s*service_selected\(\) \{[\s\S]*?^\s*\}$/m
  );
  if (!match)
    throw new Error('service_short_name/service_selected not found in Prepare deployment');
  return match[0];
}

/** Repository names the image check loop walks. */
function imageCheckRepos(): string[] {
  const loops = [...prepareScript.matchAll(/for SERVICE in ([a-z -]+); do/g)].map((m) =>
    m[1].trim()
  );
  if (loops.length === 0) throw new Error('image check loop not found');
  return loops[loops.length - 1].split(/\s+/);
}

/** Directory -> short name, as the `changes` step emits it into CHANGED_SERVICES. */
function changesStepShortNames(): Map<string, string> {
  const pairs = [
    ...changesScript.matchAll(
      /grep -q "\^(?:services\/)?([a-z-]+)\/"; then\n(?:(?!\s*fi\n).*\n)*?\s*CHANGED_SERVICES="\$CHANGED_SERVICES ([a-z-]+)"/g
    ),
  ];
  return new Map(pairs.map((m) => [m[1], m[2]]));
}

function runBash(script: string): string {
  const result = spawnSync('bash', ['-c', script], { encoding: 'utf8' });
  expect(result.stderr).toBe('');
  expect(result.status).toBe(0);
  return result.stdout.trim();
}

function shortName(repo: string): string {
  return runBash(`${mappingFunctions()}\nservice_short_name "${repo}"`);
}

function selectedRepos(changedServices: string): string[] {
  const repos = imageCheckRepos().join(' ');
  const out = runBash(
    `${mappingFunctions()}\nfor R in ${repos}; do if service_selected "$R" "${changedServices}"; then echo "$R"; fi; done`
  );
  return out ? out.split('\n') : [];
}

describe('deploy-staging image check on the selective path', () => {
  it('reads the changes step and the image check loop', () => {
    expect(changesStepShortNames().size).toBe(5);
    expect(imageCheckRepos()).toHaveLength(5);
  });

  it('maps every checked repository to the short name the changes step emits for it', () => {
    const emitted = changesStepShortNames();
    for (const repo of imageCheckRepos()) {
      expect(emitted.has(repo)).toBe(true);
      expect(shortName(repo)).toBe(emitted.get(repo));
    }
  });

  it('checks agent-service and company-user-management-service only for " agent company-management"', () => {
    expect(selectedRepos(' agent company-management').sort()).toEqual(
      ['agent-service', 'company-user-management-service'].sort()
    );
  });

  it('checks exactly batbern/staging/agent-service when only agent-service changed', () => {
    expect(selectedRepos(' agent')).toEqual(['agent-service']);
  });

  it('selects each repository by its own short name and no other', () => {
    const emitted = changesStepShortNames();
    for (const repo of imageCheckRepos()) {
      expect(selectedRepos(` ${emitted.get(repo)}`)).toEqual([repo]);
    }
  });

  it('selects nothing when no service changed', () => {
    expect(selectedRepos('')).toEqual([]);
  });
});
