import { spawnSync } from 'child_process';
import * as crypto from 'crypto';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * scripts/deploy/create-agent-signing-key.sh (agentic interaction 0.3), run for real against a
 * stub `aws` on PATH that records every call and answers from a small state directory. No real
 * AWS call is ever made.
 */
describe('create-agent-signing-key.sh', () => {
  const SCRIPT = path.resolve(__dirname, '../../../scripts/deploy/create-agent-signing-key.sh');
  const BASH = '/bin/bash';

  /**
   * The stub: one line per call in calls.log (argv joined by a unit separator). get-secret-value
   * and get-parameter answer from state/secret and state/parameter; the put calls store the
   * content of their file:// argument there. STUB_FAIL_GET=1 makes the reads fail.
   */
  const STUB = `#!/bin/bash
state="$STUB_STATE"
( IFS=$'\\x1f'; printf '%s\\n' "$*" ) >> "$state/calls.log"
arg_after() { local want="$1"; shift; while [[ $# -gt 0 ]]; do [[ "$1" == "$want" ]] && { printf '%s' "$2"; return; }; shift; done; }
from_file() { local v="$1"; [[ "$v" == file://* ]] && cat "\${v#file://}" || printf '%s' "$v"; }
case "$*" in
  *secretsmanager\\ get-secret-value*) [[ "$STUB_FAIL_GET" == 1 ]] && { echo "ResourceNotFoundException" >&2; exit 254; }; cat "$state/secret"; echo ;;
  *ssm\\ get-parameter*) [[ "$STUB_FAIL_GET" == 1 ]] && { echo "ParameterNotFound" >&2; exit 254; }; cat "$state/parameter"; echo ;;
  *secretsmanager\\ put-secret-value*) from_file "$(arg_after --secret-string "$@")" > "$state/secret"; echo v1 ;;
  *ssm\\ put-parameter*) from_file "$(arg_after --value "$@")" > "$state/parameter"; echo 2 ;;
  *) echo "stub aws: unexpected call $*" >&2; exit 2 ;;
esac
`;

  let work: string;
  let stubDir: string;
  let state: string;

  beforeEach(() => {
    work = fs.mkdtempSync(path.join(os.tmpdir(), 'agent-key-script-'));
    stubDir = path.join(work, 'bin');
    state = path.join(work, 'state');
    fs.mkdirSync(stubDir);
    fs.mkdirSync(state);
    fs.writeFileSync(path.join(stubDir, 'aws'), STUB, { mode: 0o755 });
    fs.writeFileSync(path.join(state, 'secret'), 'none');
    fs.writeFileSync(path.join(state, 'parameter'), 'none');
  });

  afterEach(() => fs.rmSync(work, { recursive: true, force: true }));

  const run = (args: string[], env: Record<string, string> = {}, pathValue?: string) =>
    spawnSync(BASH, [SCRIPT, ...args], {
      encoding: 'utf8',
      env: {
        HOME: work,
        PATH: pathValue ?? `${stubDir}:${process.env.PATH}`,
        STUB_STATE: state,
        ...env,
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

  const publicKeyIn = (stdout: string) => {
    const match = stdout.match(/-----BEGIN PUBLIC KEY-----[\s\S]+?-----END PUBLIC KEY-----/);
    expect(match).not.toBeNull();
    return match![0];
  };

  test('should_writeMatchingPairToOutDir_andCallNoAws_when_dryRun', () => {
    const out = path.join(work, 'keys');
    const result = run(['--dry-run', '--out', out]);
    expect(result.status).toBe(0);

    const privatePem = fs.readFileSync(path.join(out, 'agent-signing-private.pem'), 'utf8');
    const publicPem = fs.readFileSync(path.join(out, 'agent-signing-public.pem'), 'utf8');
    expect(fs.statSync(path.join(out, 'agent-signing-private.pem')).mode & 0o777).toBe(0o600);
    expect(privatePem).toContain('-----BEGIN PRIVATE KEY-----'); // PKCS#8

    const privateKey = crypto.createPrivateKey(privatePem);
    expect(privateKey.asymmetricKeyType).toBe('rsa');
    expect(privateKey.asymmetricKeyDetails?.modulusLength).toBe(2048);
    const signature = crypto.sign('sha256', Buffer.from('probe'), privateKey);
    expect(crypto.verify('sha256', Buffer.from('probe'), publicPem, signature)).toBe(true);

    expect(publicKeyIn(result.stdout)).toBe(publicPem.trim());
    expect(result.stdout + result.stderr).not.toContain('PRIVATE KEY');
    expect(result.stdout).toContain('put-secret-value');
    expect(calls()).toEqual([]);
  });

  test('should_refuse_when_outDirAlreadyHoldsAKey', () => {
    const out = path.join(work, 'keys');
    expect(run(['--dry-run', '--out', out]).status).toBe(0);
    const before = fs.readFileSync(path.join(out, 'agent-signing-private.pem'), 'utf8');
    const result = run(['--dry-run', '--out', out]);
    expect(result.status).not.toBe(0);
    expect(fs.readFileSync(path.join(out, 'agent-signing-private.pem'), 'utf8')).toBe(before);
  });

  test('should_writeByFileReference_andPrintRestartSteps_when_placeholdersPresent', () => {
    const result = run([]);
    expect(result.status).toBe(0);

    const all = calls();
    // Every call uses the forced profile and the fixed region.
    for (const argv of all) {
      expect(argv.slice(0, 4)).toEqual([
        '--profile',
        'batbern-staging',
        '--region',
        'eu-central-1',
      ]);
    }
    const putSecret = all.find((a) => a.includes('put-secret-value'))!;
    const putParameter = all.find((a) => a.includes('put-parameter'))!;
    expect(putSecret).toBeDefined();
    expect(putParameter).toBeDefined();
    expect(putSecret[putSecret.indexOf('--secret-id') + 1]).toBe(
      'batbern/staging/agent/signing-private-key'
    );
    expect(putSecret[putSecret.indexOf('--secret-string') + 1]).toMatch(/^file:\/\//);
    expect(putParameter[putParameter.indexOf('--name') + 1]).toBe(
      '/batbern/staging/agent/jwt-public-key'
    );
    expect(putParameter[putParameter.indexOf('--value') + 1]).toMatch(/^file:\/\//);
    expect(putParameter).toEqual(expect.arrayContaining(['--type', 'String', '--overwrite']));
    // The secret is written before the parameter.
    expect(all.indexOf(putSecret)).toBeLessThan(all.indexOf(putParameter));
    expect(all.flat().join(' ')).not.toContain('PRIVATE KEY');

    // What the stub stored: a PKCS#8 private key and the printed public key, which match.
    const storedPrivate = fs.readFileSync(path.join(state, 'secret'), 'utf8');
    const storedPublic = fs.readFileSync(path.join(state, 'parameter'), 'utf8');
    expect(storedPrivate).toContain('-----BEGIN PRIVATE KEY-----');
    expect(publicKeyIn(result.stdout)).toBe(storedPublic.trim());
    const signature = crypto.sign('sha256', Buffer.from('probe'), storedPrivate);
    expect(crypto.verify('sha256', Buffer.from('probe'), storedPublic, signature)).toBe(true);

    expect(result.stdout + result.stderr).not.toContain('PRIVATE KEY');
    expect(result.stdout).toContain('--force-new-deployment');
    expect(result.stdout).toContain('JWT decoder accepts token kinds');
  });

  test('should_useAgentKeyAwsProfile_when_profileVariableSet', () => {
    expect(run([], { AGENT_KEY_AWS_PROFILE: 'other-profile' }).status).toBe(0);
    expect(calls()[0].slice(0, 2)).toEqual(['--profile', 'other-profile']);
  });

  test.each(['secret', 'parameter'])('should_refuseAndWriteNothing_when_%sAlreadySet', (which) => {
    // positive control: the placeholder run above writes (should_writeByFileReference_...)
    fs.writeFileSync(
      path.join(state, which),
      '-----BEGIN PUBLIC KEY-----\nexisting\n-----END PUBLIC KEY-----'
    );
    const result = run([]);
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('already set');
    expect(calls().length).toBeGreaterThan(0);
    expect(calls().some((a) => a.includes('put-secret-value') || a.includes('put-parameter'))).toBe(
      false
    );
  });

  test('should_overwriteWithWarning_when_forceGiven', () => {
    fs.writeFileSync(path.join(state, 'secret'), 'old-key');
    const result = run(['--force']);
    expect(result.status).toBe(0);
    expect(result.stderr).toContain('--force');
    expect(fs.readFileSync(path.join(state, 'secret'), 'utf8')).toContain(
      '-----BEGIN PRIVATE KEY-----'
    );
  });

  test('should_sayDeployFirst_when_resourcesMissing', () => {
    const result = run([], { STUB_FAIL_GET: '1' });
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('Deploy the SecretsStack first');
    expect(calls().some((a) => a.includes('put-secret-value') || a.includes('put-parameter'))).toBe(
      false
    );
  });

  test('should_exitBeforeAnyAwsCall_when_opensslMissing', () => {
    // PATH holds only the stub aws: openssl is not found.
    const result = run([], {}, stubDir);
    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain('openssl');
    expect(calls()).toEqual([]);
  });
});
