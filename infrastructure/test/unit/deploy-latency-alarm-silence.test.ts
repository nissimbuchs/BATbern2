import { spawnSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as yaml from 'js-yaml';

/**
 * #1144: batbern-staging-alb-latency-p95 flapped through every rolling deploy (about 45 min,
 * 22 mails per deploy). Every job that rolls ECS services now mutes the alarm's actions before
 * its first rollout and re-enables them after the services are stable again.
 *
 * The dangerous failure is the re-enable not running: a failed or cancelled run would leave
 * the production latency alarm mute with nobody noticing. These tests pin, for EVERY job that
 * rolls services (found by the commands it runs, not by step names), that the re-enable step
 * runs under a plain `always()`, after the last rollout and the stability wait, with fresh
 * credentials, and that no toggle can fail the run.
 */

interface Step {
  name?: string;
  id?: string;
  if?: string;
  uses?: string;
  run?: string;
  'continue-on-error'?: boolean;
}
interface Workflow {
  on?: unknown;
  env?: Record<string, string>;
  jobs: Record<string, { steps?: Step[]; uses?: string }>;
}

const repoRoot = path.resolve(__dirname, '../../..');
const workflowsDir = path.join(repoRoot, '.github/workflows');
const loadWorkflow = (file: string): Workflow =>
  yaml.load(fs.readFileSync(path.join(workflowsDir, file), 'utf8')) as Workflow;

const workflow = loadWorkflow('deploy-staging.yml');

/**
 * A step rolls ECS tasks when it deploys a service stack or touches a service directly.
 * Stack names: the four `BATbern-<env>-<Service>` stacks; `ApiGateway` alone (AWS API Gateway)
 * and `Frontend` do not run ECS tasks.
 */
const ROLLS_SERVICES =
  /update-ecs-task\.sh|rollback-deployment\.sh|ecs update-service|deploy:staging:layer4-|cdk deploy[^\n]*BATbern-[^\s]*-(EventManagement|CompanyManagement|PartnerCoordination|ApiGatewayService)\b/;
const DISABLE = /latency-alarm-actions\.sh disable /;
const ENABLE = /latency-alarm-actions\.sh enable /;
const WAIT_STABLE = /wait-ecs-services-stable\.sh /;

const indexes = (steps: Step[], re: RegExp): number[] =>
  steps.flatMap((s, i) => (s.run && re.test(s.run) ? [i] : []));

const rollingJobs = Object.entries(workflow.jobs).filter(
  ([, job]) => indexes(job.steps ?? [], ROLLS_SERVICES).length > 0
);

describe('deploy-staging.yml mutes the latency alarm while services roll (#1144)', () => {
  test('should_findTheDeployAndTheRollbackJob_when_scanningForServiceRollouts', () => {
    expect(rollingJobs.map(([name]) => name).sort()).toEqual([
      'deploy-to-staging',
      'rollback-on-test-failure',
    ]);
  });

  test('should_targetTheWorkflowEnvironment_when_toggling', () => {
    expect(workflow.env?.ENVIRONMENT).toBe('staging');
    for (const [, job] of rollingJobs) {
      const steps = job.steps ?? [];
      for (const i of [...indexes(steps, DISABLE), ...indexes(steps, ENABLE)]) {
        expect(steps[i].run).toMatch(
          /latency-alarm-actions\.sh (disable|enable) "\$\{\{ env\.ENVIRONMENT \}\}"/
        );
      }
    }
  });

  describe.each(rollingJobs.map(([name, job]) => [name, job.steps ?? []] as const))(
    'job %s',
    (_name, steps) => {
      const rollouts = indexes(steps, ROLLS_SERVICES);
      const disables = indexes(steps, DISABLE);
      const enables = indexes(steps, ENABLE);
      const waits = indexes(steps, WAIT_STABLE);

      test('should_disableBeforeTheFirstRollout', () => {
        expect(disables.length).toBeGreaterThan(0);
        expect(Math.min(...disables)).toBeLessThan(Math.min(...rollouts));
      });

      test('should_waitForStableServicesAfterTheLastRollout', () => {
        expect(waits.length).toBeGreaterThan(0);
        expect(Math.max(...waits)).toBeGreaterThan(Math.max(...rollouts));
      });

      test('should_reenableUnderPlainAlways_afterTheStabilityWait', () => {
        expect(enables).toHaveLength(1);
        const enable = enables[0];
        // Exactly `always()`: anything narrower (`always() && success()`, a path condition)
        // reopens the hole this step exists to close.
        expect(steps[enable].if).toBe('always()');
        expect(enable).toBeGreaterThan(Math.max(...rollouts, ...waits));
      });

      test('should_refreshCredentialsUnderPlainAlways_rightBeforeReenabling', () => {
        // A long run outlives the role session, and a failed run skips the ordinary
        // re-authenticate steps; the enable call must not run on an expired session.
        const before = steps[enables[0] - 1];
        expect(before.uses).toMatch(/^aws-actions\/configure-aws-credentials@/);
        expect(before.if).toBe('always()');
      });

      test('should_neverFailTheRun_when_aToggleFails', () => {
        // Before the CICD stack carrying the grant is deployed the calls are denied. That is
        // a warning, not a red run.
        for (const i of [...disables, ...enables, enables[0] - 1]) {
          expect(steps[i]['continue-on-error']).toBe(true);
        }
      });
    }
  );

  test('should_gateEachDisableByItsDeployPath_inTheDeployJob', () => {
    const steps = workflow.jobs['deploy-to-staging'].steps ?? [];
    const fastPath = steps.findIndex((s) => s.name === 'Fast-path code deployment');
    const disables = indexes(steps, DISABLE);
    const fastPathDisable = disables.find((i) => i < fastPath) as number;
    expect(steps[fastPathDisable].if).toContain("code_only == 'true'");
    const cdkRollouts = indexes(steps, ROLLS_SERVICES).filter((i) => i > fastPath);
    const cdkDisable = disables.find((i) => i > fastPath && i < Math.min(...cdkRollouts));
    expect(cdkDisable).toBeDefined();
    expect(steps[cdkDisable as number].if).toContain("code_only != 'true'");
    expect(steps[(cdkDisable as number) - 1].uses).toMatch(/configure-aws-credentials/);
  });

  test('should_rollServicesOnlyFromDeployStaging_when_scanningOtherWorkflows', () => {
    // deploy-code-staging.yml is a workflow_call-only fast path that no workflow calls
    // (checked 2026-11-19). If anything starts calling it, it needs the same mute.
    const others = fs
      .readdirSync(workflowsDir)
      .filter((f) => /\.ya?ml$/.test(f) && f !== 'deploy-staging.yml');
    const rolling = others.filter((f) =>
      Object.values(loadWorkflow(f).jobs ?? {}).some(
        (job) => indexes(job.steps ?? [], ROLLS_SERVICES).length > 0
      )
    );
    expect(rolling).toEqual(['deploy-code-staging.yml']);
    const callers = fs
      .readdirSync(workflowsDir)
      .filter((f) =>
        fs.readFileSync(path.join(workflowsDir, f), 'utf8').includes('deploy-code-staging.yml')
      );
    expect(callers).toEqual([]);
  });
});

describe('infrastructure change detection in deploy-staging.yml', () => {
  // A config change (e.g. an alarm threshold in lib/config/staging-config.ts) must take the
  // CDK path. The pattern used to list lib/stacks, lib/lambda, lib/constructs, bin and lambda
  // but not lib/config, so a config-only change was classified code-only and never deployed.
  const raw = fs.readFileSync(path.join(workflowsDir, 'deploy-staging.yml'), 'utf8');
  const match = raw.match(
    /grep -qE "(\^infrastructure\/[^"]+)"; then\s*\n\s*echo "✓ Infrastructure/
  );
  const infraChange = new RegExp((match as RegExpMatchArray)[1]);

  test.each([
    'infrastructure/lib/config/staging-config.ts',
    'infrastructure/lib/stacks/cicd-stack.ts',
    'infrastructure/lib/constructs/alb-alarms.ts',
    'infrastructure/lib/lambda/x/index.ts',
    'infrastructure/lambda/x/index.ts',
    'infrastructure/bin/batbern-infrastructure.ts',
    'infrastructure/package.json',
  ])('should_detectInfrastructureChange_for_%s', (file) => {
    expect(infraChange.test(file)).toBe(true);
  });

  test.each(['infrastructure/test/unit/x.test.ts', 'docs/architecture/x.md'])(
    'should_notDetectInfrastructureChange_for_%s',
    (file) => {
      expect(infraChange.test(file)).toBe(false);
    }
  );
});

/**
 * The CI scripts run against a stub `aws` on PATH that records every call and answers from
 * environment variables, so the control flow is tested without touching AWS.
 */
describe('CI scripts around the rollout (stub aws)', () => {
  const scriptsDir = path.join(repoRoot, 'scripts/ci');
  let stubDir: string;
  let log: string;

  beforeEach(() => {
    stubDir = fs.mkdtempSync(path.join(os.tmpdir(), 'aws-stub-'));
    log = path.join(stubDir, 'calls.log');
    fs.writeFileSync(
      path.join(stubDir, 'aws'),
      `#!/usr/bin/env bash
echo "$*" >> "$AWS_STUB_LOG"
case "$*" in
  *"ecs list-services"*)
    for f in $AWS_STUB_SERVICES; do
      if [[ "$*" == *"-$f-Service"* ]]; then
        echo "arn:aws:ecs:eu-central-1:123456789012:service/batbern-staging/BATbern-staging-$f-ServiceABC"
        exit 0
      fi
    done
    echo None ;;
  *"ecs wait services-stable"*) exit "\${AWS_STUB_WAIT_RC:-0}" ;;
  *"describe-alarms"*) echo "\${AWS_STUB_STATE:-OK}" ;;
  *"enable-alarm-actions"*) exit "\${AWS_STUB_ENABLE_RC:-0}" ;;
  *"disable-alarm-actions"*) exit "\${AWS_STUB_DISABLE_RC:-0}" ;;
  *"set-alarm-state"*) exit 0 ;;
esac
exit 0
`,
      { mode: 0o755 }
    );
  });

  afterEach(() => {
    fs.rmSync(stubDir, { recursive: true, force: true });
  });

  const run = (script: string, args: string[], env: Record<string, string> = {}) => {
    const result = spawnSync('bash', [path.join(scriptsDir, script), ...args], {
      env: { ...process.env, PATH: `${stubDir}:${process.env.PATH}`, AWS_STUB_LOG: log, ...env },
      encoding: 'utf8',
    });
    const calls = fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n') : [];
    return { status: result.status, out: `${result.stdout}${result.stderr}`, calls };
  };

  describe('wait-ecs-services-stable.sh', () => {
    const ALL = 'CompanyManagement EventManagement PartnerCoordination ApiGatewayService';

    test('should_waitForAllFourServicesIncludingTheGateway_byTheirRealNames', () => {
      // The step it replaces waited for `event-management-service` etc., which ECS reports as
      // MISSING (real names are BATbern-staging-<Service>-Service<hash>), so it waited for
      // nothing, and it never listed the API gateway.
      const { status, calls } = run('wait-ecs-services-stable.sh', ['staging'], {
        AWS_STUB_SERVICES: ALL,
      });
      expect(status).toBe(0);
      const waited = calls.filter((c) => c.includes('ecs wait services-stable'));
      expect(waited).toHaveLength(4);
      for (const f of ALL.split(' ')) {
        expect(waited.some((c) => c.includes(`--services BATbern-staging-${f}-ServiceABC`))).toBe(
          true
        );
      }
    });

    test('should_failLoudly_when_aServiceNameResolvesToNothing', () => {
      const { status, out } = run('wait-ecs-services-stable.sh', ['staging'], {
        AWS_STUB_SERVICES: 'CompanyManagement EventManagement PartnerCoordination',
      });
      expect(status).not.toBe(0);
      expect(out).toContain('::error::');
      expect(out).toContain('ApiGatewayService');
    });

    test('should_warnButSucceed_when_aServiceDoesNotSettle', () => {
      const { status, out } = run('wait-ecs-services-stable.sh', ['staging'], {
        AWS_STUB_SERVICES: ALL,
        AWS_STUB_WAIT_RC: '255',
      });
      expect(status).toBe(0);
      expect(out).toContain('::warning::');
    });
  });

  describe('latency-alarm-actions.sh', () => {
    const ALARM = 'batbern-staging-alb-latency-p95';

    test('should_disableOnlyTheLatencyAlarm_when_disabling', () => {
      const { status, calls } = run('latency-alarm-actions.sh', ['disable', 'staging']);
      expect(status).toBe(0);
      expect(calls).toHaveLength(1);
      expect(calls[0]).toContain(`cloudwatch disable-alarm-actions --alarm-names ${ALARM}`);
    });

    test('should_resetToOkBeforeEnabling_when_alarmFiredWhileMuted', () => {
      // Actions fire on state CHANGES only. An alarm that went to ALARM while muted would
      // never mail; resetting it lets the next evaluation alarm again if the breach persists.
      // The reset happens while still muted, so it sends no OK mail of its own.
      const { status, calls } = run('latency-alarm-actions.sh', ['enable', 'staging'], {
        AWS_STUB_STATE: 'ALARM',
      });
      expect(status).toBe(0);
      const reset = calls.findIndex((c) => c.includes('cloudwatch set-alarm-state'));
      const enable = calls.findIndex((c) => c.includes('cloudwatch enable-alarm-actions'));
      expect(reset).toBeGreaterThanOrEqual(0);
      expect(calls[reset]).toContain(`--alarm-name ${ALARM}`);
      expect(calls[reset]).toContain('--state-value OK');
      expect(calls[reset]).toContain('reset after deploy (#1144)');
      expect(enable).toBeGreaterThan(reset);
    });

    test('should_notResetState_when_alarmIsOk', () => {
      const { status, calls } = run('latency-alarm-actions.sh', ['enable', 'staging'], {
        AWS_STUB_STATE: 'OK',
      });
      expect(status).toBe(0);
      expect(calls.some((c) => c.includes('set-alarm-state'))).toBe(false);
      expect(calls.some((c) => c.includes(`enable-alarm-actions --alarm-names ${ALARM}`))).toBe(
        true
      );
    });

    test('should_warnAndExitZero_when_theCallIsDenied', () => {
      for (const [action, env] of [
        ['disable', { AWS_STUB_DISABLE_RC: '254' }],
        ['enable', { AWS_STUB_ENABLE_RC: '254' }],
      ] as const) {
        const { status, out } = run('latency-alarm-actions.sh', [action, 'staging'], env);
        expect(status).toBe(0);
        expect(out).toContain('::warning::');
      }
    });
  });
});
