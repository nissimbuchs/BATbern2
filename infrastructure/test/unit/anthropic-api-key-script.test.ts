import { spawnSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * scripts/deploy/put-anthropic-api-key.sh (agent runtime story 3), run for real against a stub
 * `aws` on PATH that records every call and answers from a small state directory. No real AWS call
 * is ever made, and every key here is fake.
 */
describe('put-anthropic-api-key.sh', () => {
  const SCRIPT = path.resolve(__dirname, '../../../scripts/deploy/put-anthropic-api-key.sh');
  const BASH = '/bin/bash';
  const FAKE_KEY = 'sk-ant-api03-FAKE-test-key-not-real-0000';
  const AGENT_ARN =
    'arn:aws:ecs:eu-central-1:123456789012:service/batbern-staging/BATbern-staging-AgentService-Service-abc';
  const OTHER_ARN =
    'arn:aws:ecs:eu-central-1:123456789012:service/batbern-staging/BATbern-staging-AgentService-Service-def';

  /**
   * The stub: one line per call in calls.log (argv joined by a unit separator). get-secret-value
   * answers from state/secret; put-secret-value stores the content of its file:// argument there;
   * list-services answers the ARNs in STUB_ARNS (tab separated, as --output text does);
   * update-service answers a service name.
   */
  const STUB = `#!/bin/bash
state="$STUB_STATE"
( IFS=$'\\x1f'; printf '%s\\n' "$*" ) >> "$state/calls.log"
arg_after() { local want="$1"; shift; while [[ $# -gt 0 ]]; do [[ "$1" == "$want" ]] && { printf '%s' "$2"; return; }; shift; done; }
from_file() { local v="$1"; [[ "$v" == file://* ]] && cat "\${v#file://}" || printf '%s' "$v"; }
case "$*" in
  *secretsmanager\\ get-secret-value*) cat "$state/secret"; echo ;;
  *secretsmanager\\ put-secret-value*) from_file "$(arg_after --secret-string "$@")" > "$state/secret"; echo v1 ;;
  *ecs\\ list-services*) printf '%s\\n' "$STUB_ARNS" ;;
  *ecs\\ update-service*) echo BATbern-staging-AgentService-Service ;;
  *) echo "stub aws: unexpected call $*" >&2; exit 2 ;;
esac
`;

  let work: string;
  let stubDir: string;
  let state: string;

  beforeEach(() => {
    work = fs.mkdtempSync(path.join(os.tmpdir(), 'anthropic-key-script-'));
    stubDir = path.join(work, 'bin');
    state = path.join(work, 'state');
    fs.mkdirSync(stubDir);
    fs.mkdirSync(state);
    fs.writeFileSync(path.join(stubDir, 'aws'), STUB, { mode: 0o755 });
    fs.writeFileSync(path.join(state, 'secret'), 'none');
  });

  afterEach(() => fs.rmSync(work, { recursive: true, force: true }));

  const run = (args: string[], input: string, arns: string[] = [AGENT_ARN]) =>
    spawnSync(BASH, [SCRIPT, ...args], {
      encoding: 'utf8',
      input,
      env: {
        HOME: work,
        PATH: `${stubDir}:${process.env.PATH}`,
        STUB_STATE: state,
        STUB_ARNS: arns.join('\t'),
      },
    });

  const calls = (): string[][] => {
    const log = path.join(state, 'calls.log');
    if (!fs.existsSync(log)) return [];
    return fs
      .readFileSync(log, 'utf8')
      .split('\n')
      .filter((l) => l.length > 0)
      .map((l) => l.split('\x1f'));
  };

  const callsLog = () => {
    const log = path.join(state, 'calls.log');
    return fs.existsSync(log) ? fs.readFileSync(log, 'utf8') : '';
  };

  const secret = () => fs.readFileSync(path.join(state, 'secret'), 'utf8');
  const has = (word: string) => calls().some((a) => a.includes(word));

  test('should_writeKeyByFileReference_andNeverShowIt_when_placeholderPresent', () => {
    const result = run([], `${FAKE_KEY}\n`);
    expect(result.status).toBe(0);

    const all = calls();
    for (const argv of all) {
      expect(argv.slice(0, 4)).toEqual([
        '--profile',
        'batbern-staging',
        '--region',
        'eu-central-1',
      ]);
    }
    const put = all.find((a) => a.includes('put-secret-value'))!;
    expect(put).toBeDefined();
    expect(put[put.indexOf('--secret-id') + 1]).toBe('batbern/staging/agent/anthropic-api-key');
    expect(put[put.indexOf('--secret-string') + 1]).toMatch(/^file:\/\//);
    expect(secret()).toBe(FAKE_KEY);

    // The key is in none of stdout, stderr or the recorded argv
    expect(result.stdout).not.toContain(FAKE_KEY);
    expect(result.stderr).not.toContain(FAKE_KEY);
    expect(callsLog()).not.toContain(FAKE_KEY);
    expect(callsLog()).not.toContain('FAKE-test-key');
  });

  test('should_restartAgentService_withForceNewDeployment_when_written', () => {
    const result = run([], `${FAKE_KEY}\n`);
    expect(result.status).toBe(0);

    const update = calls().find((a) => a.includes('update-service'))!;
    expect(update).toBeDefined();
    expect(update[update.indexOf('--cluster') + 1]).toBe('batbern-staging');
    expect(update[update.indexOf('--service') + 1]).toBe(AGENT_ARN);
    expect(update).toContain('--force-new-deployment');
    // The write happens before the restart
    const all = calls();
    expect(all.findIndex((a) => a.includes('put-secret-value'))).toBeLessThan(
      all.findIndex((a) => a.includes('update-service'))
    );
    expect(result.stdout).toContain('"Model adapter"');
  });

  test('should_refuseAndWriteNothing_when_keyAlreadySet', () => {
    fs.writeFileSync(path.join(state, 'secret'), 'sk-ant-api03-FAKE-existing-key');
    const result = run([], `${FAKE_KEY}\n`);
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('--force');
    // positive control: the secret was read
    expect(has('get-secret-value')).toBe(true);
    expect(has('put-secret-value')).toBe(false);
    expect(has('update-service')).toBe(false);
    expect(secret()).toBe('sk-ant-api03-FAKE-existing-key');
    expect(result.stdout + result.stderr).not.toContain('FAKE-existing-key');
  });

  test('should_replaceKey_when_forceGiven', () => {
    fs.writeFileSync(path.join(state, 'secret'), 'sk-ant-api03-FAKE-existing-key');
    const result = run(['--force'], `${FAKE_KEY}\n`);
    expect(result.status).toBe(0);
    expect(result.stderr).toContain('--force');
    expect(secret()).toBe(FAKE_KEY);
    expect(has('update-service')).toBe(true);
  });

  test.each([
    ['none', 'none\n'],
    ['NONE with spaces', '  NONE \n'],
    ['blank', '   \n'],
    ['empty', ''],
  ])('should_refuseWithoutAwsCall_when_stdinIs_%s', (_name, input) => {
    const result = run([], input);
    expect(result.status).not.toBe(0);
    expect(calls()).toEqual([]);
    expect(secret()).toBe('none');
  });

  test('should_writePlaceholder_when_noneWithForce', () => {
    fs.writeFileSync(path.join(state, 'secret'), 'sk-ant-api03-FAKE-existing-key');
    const result = run(['--force'], 'none\n');
    expect(result.status).toBe(0);
    expect(secret()).toBe('none');
    expect(result.stdout).toContain('adapter is off');
  });

  test('should_makeNoEcsCall_when_noRestart', () => {
    const result = run(['--no-restart'], `${FAKE_KEY}\n`);
    expect(result.status).toBe(0);
    expect(secret()).toBe(FAKE_KEY);
    expect(calls().some((a) => a.includes('ecs'))).toBe(false);
    expect(result.stdout).toContain('--force-new-deployment');
  });

  test('should_makeNoAwsCallAtAll_when_dryRun', () => {
    const result = run(['--dry-run'], `${FAKE_KEY}\n`);
    expect(result.status).toBe(0);
    expect(calls()).toEqual([]);
    expect(secret()).toBe('none');
    expect(result.stdout + result.stderr).not.toContain(FAKE_KEY);
    expect(result.stdout).toContain('put-secret-value');
  });

  test.each([
    ['zero', [] as string[]],
    ['two', [AGENT_ARN, OTHER_ARN]],
  ])('should_failAfterWrite_withoutRestart_when_%sAgentServicesFound', (_name, arns) => {
    const result = run([], `${FAKE_KEY}\n`, arns);
    expect(result.status).not.toBe(0);
    expect(secret()).toBe(FAKE_KEY);
    expect(has('list-services')).toBe(true);
    expect(has('update-service')).toBe(false);
    expect(result.stderr).toContain('The key is written');
  });
});
