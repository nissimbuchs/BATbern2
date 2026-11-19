import { spawnSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

/**
 * Every `run:` block of every workflow is valid bash.
 *
 * Found by the first deploy of PR #1324: a step's `run:` was shortened to one script call, but
 * the old inline loop stayed below it. YAML folds such lines into the plain scalar, so the step
 * ran `./script.sh ... for service in ...; do if ...` and bash failed with "unexpected end of
 * file" after the services were already stable, failing a good deploy. Nothing before the
 * deploy executed that string. `bash -n` on each block catches the class without running it.
 */

interface Step {
  name?: string;
  run?: string;
  shell?: string;
}
interface Workflow {
  defaults?: { run?: { shell?: string } };
  jobs?: Record<string, { steps?: Step[]; defaults?: { run?: { shell?: string } } }>;
}

const workflowsDir = path.resolve(__dirname, '../../../.github/workflows');
const workflowFiles = fs
  .readdirSync(workflowsDir)
  .filter((f) => f.endsWith('.yml') || f.endsWith('.yaml'));

// GitHub substitutes expressions before bash sees the script; a neutral word keeps the syntax.
const withoutExpressions = (script: string): string =>
  script.replace(/\$\{\{[\s\S]*?\}\}/g, 'EXPR');

const isBash = (shell: string | undefined): boolean =>
  shell === undefined || /^(bash|sh)\b/.test(shell);

const runBlocks = workflowFiles.flatMap((file) => {
  const wf = yaml.load(fs.readFileSync(path.join(workflowsDir, file), 'utf8')) as Workflow;
  return Object.entries(wf.jobs ?? {}).flatMap(([jobId, job]) =>
    (job.steps ?? [])
      .filter((step) => typeof step.run === 'string')
      .filter((step) => isBash(step.shell ?? job.defaults?.run?.shell ?? wf.defaults?.run?.shell))
      .map((step, i) => ({
        label: `${file} › ${jobId} › ${step.name ?? `run #${i + 1}`}`,
        script: step.run as string,
      }))
  );
});

describe('workflow run blocks', () => {
  it('finds the run blocks of the deploy workflow', () => {
    expect(
      runBlocks.filter((b) => b.label.startsWith('deploy-staging.yml')).length
    ).toBeGreaterThan(20);
  });

  it.each(runBlocks.map((b) => [b.label, b.script]))('%s is valid bash', (_label, script) => {
    const result = spawnSync('bash', ['-n'], {
      input: withoutExpressions(script),
      encoding: 'utf8',
    });
    expect(result.stderr).toBe('');
    expect(result.status).toBe(0);
  });
});
