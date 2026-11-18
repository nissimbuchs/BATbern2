import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const SCRIPT = path.join(ROOT, 'scripts', 'update-versions.js');
const REAL_VERSIONS = path.join(ROOT, 'docs', 'versions.json');
const { readGradleVersion, readNodeVersion } = createRequire(import.meta.url)(SCRIPT);

/**
 * Tests for scripts/update-versions.js — #991.
 *
 * The bug these pin: `lastUpdated` is a top-level scalar in versions.json that the generator
 * sets to today's date, and `compareVersions` iterated EVERY key. So on any day later than the
 * file's own date, the script reported drift, rewrote the file, and `sync-versions.yml` then
 * committed a diff whose entire content was a date bump — pushing a bot commit onto the PR
 * branch, which re-triggered every workflow and stalled the PR behind `action_required`.
 *
 * It also meant `--check` mode was unusable in CI: it would have failed every day regardless of
 * whether any dependency had actually moved.
 *
 * Each test runs the real script against a temporary copy of versions.json, via the
 * BATBERN_VERSIONS_FILE override, so nothing here touches the checked-in file.
 */

function withTempVersions(mutate) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'batbern-versions-'));
  const file = path.join(dir, 'versions.json');
  const content = JSON.parse(fs.readFileSync(REAL_VERSIONS, 'utf8'));
  mutate(content);
  fs.writeFileSync(file, JSON.stringify(content, null, 2) + '\n');
  return { file, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

function run(file, args) {
  try {
    const stdout = execFileSync('node', [SCRIPT, ...args], {
      env: { ...process.env, BATBERN_VERSIONS_FILE: file },
      encoding: 'utf8',
    });
    return { code: 0, stdout };
  } catch (error) {
    return { code: error.status, stdout: `${error.stdout ?? ''}${error.stderr ?? ''}` };
  }
}

const STALE_DATE = '1999-01-01';

test('--check passes when only lastUpdated is stale', () => {
  // The whole bug. A date that is merely old is not drift, and must not fail CI or provoke a
  // rewrite. Before the fix this exited 1 on any day after the file was last written.
  const { file, cleanup } = withTempVersions((v) => {
    v.lastUpdated = STALE_DATE;
  });
  try {
    const { code, stdout } = run(file, ['--check']);
    assert.equal(code, 0, `expected clean check, got exit ${code}:\n${stdout}`);
  } finally {
    cleanup();
  }
});

test('--check fails when a real version has drifted', () => {
  // The guarantee the workflow exists for must survive the fix.
  const { file, cleanup } = withTempVersions((v) => {
    v.frontend.react = '1.x';
  });
  try {
    const { code, stdout } = run(file, ['--check']);
    assert.equal(code, 1, `expected drift to fail, got exit ${code}:\n${stdout}`);
    assert.match(stdout, /frontend\.react/);
  } finally {
    cleanup();
  }
});

test('a plain run leaves the file byte-identical when only lastUpdated is stale', () => {
  // This is what produced the no-op bot commits: the file was rewritten purely to move the
  // date, and `git diff --quiet` in the workflow then saw a dirty tree.
  const { file, cleanup } = withTempVersions((v) => {
    v.lastUpdated = STALE_DATE;
  });
  try {
    const before = fs.readFileSync(file, 'utf8');
    const { code } = run(file, []);
    const after = fs.readFileSync(file, 'utf8');
    assert.equal(code, 0);
    assert.equal(after, before, 'file was rewritten despite no version change');
    assert.match(after, new RegExp(STALE_DATE), 'stale date must be left alone');
  } finally {
    cleanup();
  }
});

test('a plain run rewrites the file and bumps lastUpdated when a version really changed', () => {
  // The date is meaningful exactly when something moved, which is the only time it is written.
  const { file, cleanup } = withTempVersions((v) => {
    v.lastUpdated = STALE_DATE;
    v.frontend.react = '1.x';
  });
  try {
    const { code } = run(file, []);
    const after = JSON.parse(fs.readFileSync(file, 'utf8'));
    assert.equal(code, 0);
    assert.notEqual(after.frontend.react, '1.x', 'real drift should be corrected');
    assert.notEqual(after.lastUpdated, STALE_DATE, 'lastUpdated should move with a real change');
  } finally {
    cleanup();
  }
});

test('--check ignores the metadata header, not just lastUpdated', () => {
  // $schema and description are prose about the file, not versions of anything. Treating them
  // as drift would make the check fail on an editorial change.
  const { file, cleanup } = withTempVersions((v) => {
    v.description = 'edited by a human';
  });
  try {
    const { code, stdout } = run(file, ['--check']);
    assert.equal(code, 0, `metadata must not count as drift, got exit ${code}:\n${stdout}`);
  } finally {
    cleanup();
  }
});

test('the checked-in versions.json is currently in sync', () => {
  // Guards the guard: if this repo's own file were stale, every other assertion here could
  // pass for the wrong reason.
  const { code, stdout } = run(REAL_VERSIONS, ['--check']);
  assert.equal(code, 0, `docs/versions.json has drifted:\n${stdout}`);
});

/**
 * #1211: Gradle and Node were hard-coded ('8.x', '20'), so versions.json claimed Gradle 8 and
 * Node 20 while the estate ran Gradle 9 and Node 22, and --check passed because generator and
 * file agreed on the same wrong value. They are now read from the wrapper and the workflows.
 */

function tempDir() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'batbern-versions-src-'));
  return { dir, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

test('Gradle version comes from the wrapper distributionUrl', () => {
  const { dir, cleanup } = tempDir();
  try {
    const wrapper = path.join(dir, 'gradle-wrapper.properties');
    fs.writeFileSync(wrapper, [
      'distributionBase=GRADLE_USER_HOME',
      'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.2-bin.zip',
      '',
    ].join('\n'));
    assert.equal(readGradleVersion(wrapper), '9.x');

    fs.writeFileSync(wrapper, 'distributionUrl=https\\://services.gradle.org/distributions/gradle-10.0-all.zip\n');
    assert.equal(readGradleVersion(wrapper), '10.x');

    assert.equal(readGradleVersion(path.join(dir, 'missing.properties')), null);
  } finally {
    cleanup();
  }
});

test('Node version comes from the active workflows, ignoring disabled files and comments', () => {
  const { dir, cleanup } = tempDir();
  try {
    fs.writeFileSync(path.join(dir, 'build.yml'), "      - uses: actions/setup-node@v6\n        with:\n          node-version: '22'\n");
    fs.writeFileSync(path.join(dir, 'other.yaml'), '          node-version: 22.11.0\n          # node-version: 18\n');
    fs.writeFileSync(path.join(dir, 'old.yml.disabled'), "          node-version: '20'\n");
    assert.equal(readNodeVersion(dir, path.join(dir, 'no-nvmrc')), '22');
  } finally {
    cleanup();
  }
});

test('Node version: workflows that disagree fail with the files named', () => {
  const { dir, cleanup } = tempDir();
  try {
    fs.writeFileSync(path.join(dir, 'a.yml'), "          node-version: '22'\n");
    fs.writeFileSync(path.join(dir, 'b.yml'), "          node-version: '24'\n");
    assert.throws(() => readNodeVersion(dir, path.join(dir, 'no-nvmrc')), /22 \(a\.yml\); 24 \(b\.yml\)/);
  } finally {
    cleanup();
  }
});

test('Node version: .nvmrc wins over the workflows', () => {
  const { dir, cleanup } = tempDir();
  try {
    fs.writeFileSync(path.join(dir, 'a.yml'), "          node-version: '22'\n");
    const nvmrc = path.join(dir, '.nvmrc');
    fs.writeFileSync(nvmrc, 'v24.1.0\n');
    assert.equal(readNodeVersion(dir, nvmrc), '24');
  } finally {
    cleanup();
  }
});

test('the checked-in versions.json states the real Gradle wrapper and CI Node versions', () => {
  // Pins the reported truth itself, not only the readers: this repo runs the wrapper's Gradle
  // and the workflows' Node, and the file must say so.
  const real = JSON.parse(fs.readFileSync(REAL_VERSIONS, 'utf8'));
  assert.equal(real.backend.gradle, readGradleVersion());
  assert.equal(real.infrastructure.node, readNodeVersion());
  assert.notEqual(real.backend.gradle, '8.x');
});
