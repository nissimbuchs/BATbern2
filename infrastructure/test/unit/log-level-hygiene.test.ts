import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

/**
 * Repo-wide invariant: no deployed profile may default `ch.batbern` to DEBUG (#990).
 *
 * WHY THIS FILE EXISTS. api-gateway ran at DEBUG in production for as long as anyone can
 * remember. One 24h CloudWatch Insights query over its single log group scanned 295,644 records,
 * and every one of those lines was ingested twice until the duplicate appender was removed.
 *
 * The first attempt to fix it edited `logback-spring.xml` and did nothing at all, because Spring
 * Boot applies `logging.level.*` from application.yml AFTER the logback configuration and
 * overrides it. The change looked correct, passed review, deployed — and DEBUG lines kept
 * arriving. It was only caught by reading the live log group afterwards.
 *
 * A second, quieter instance: company-user-management pinned `ch.batbern: DEBUG` under the
 * `staging` profile while setting INFO under `production`. That reads as correct and is not,
 * because this estate runs SPRING_PROFILES_ACTIVE=staging IN the production account — the
 * production block is unreachable config.
 *
 * Neither instance is visible from the code alone; both need the deployment context. Hence a
 * test that encodes the context.
 *
 * `local` and `test` profiles are exempt — DEBUG is the right default when a human is watching.
 */
describe('log level hygiene (#990)', () => {
  const ROOT = path.join(__dirname, '..', '..', '..');
  const EXEMPT_PROFILES = new Set(['local', 'test', 'development', 'dev']);

  const configFiles = (): string[] => {
    const roots = [path.join(ROOT, 'api-gateway'), path.join(ROOT, 'services')];
    const found: string[] = [];
    const walk = (dir: string) => {
      if (!fs.existsSync(dir)) return;
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        // src/main only: build/ holds compiled copies and src/test is exempt by definition.
        if (entry.isDirectory()) {
          if (entry.name === 'build' || entry.name === 'node_modules' || entry.name === 'test') continue;
          walk(full);
        } else if (/^application.*\.ya?ml$/.test(entry.name) && full.includes(`${path.sep}main${path.sep}`)) {
          found.push(full);
        }
      }
    };
    roots.forEach(walk);
    return found;
  };

  /** Every (file, profile, level) triple that sets ch.batbern, across all YAML documents. */
  const declarations = () => {
    const out: Array<{ file: string; profile: string; level: string }> = [];
    for (const file of configFiles()) {
      const docs = yaml.loadAll(fs.readFileSync(file, 'utf8')) as any[];
      for (const doc of docs) {
        if (!doc) continue;
        const level = doc?.logging?.level?.['ch.batbern'];
        if (level === undefined) continue;
        const profile = doc?.spring?.config?.activate?.['on-profile'] ?? 'default';
        out.push({ file: path.relative(ROOT, file), profile: String(profile), level: String(level) });
      }
    }
    return out;
  };

  /** DEBUG, or a placeholder whose DEFAULT is DEBUG — `${LOG_LEVEL:DEBUG}` is only safe while
   *  every environment remembers to set LOG_LEVEL, which is the coupling that hid #990. */
  const defaultsToDebug = (level: string) =>
    level.trim().toUpperCase() === 'DEBUG' || /^\$\{[^:]+:\s*DEBUG\s*\}$/i.test(level.trim());

  test('should_findConfigsToCheck_when_suiteRuns', () => {
    // Guards the guard: a walk that silently finds nothing would pass every assertion below.
    expect(declarations().length).toBeGreaterThanOrEqual(3);
  });

  test('should_neverDefaultToDebug_when_profileIsDeployed', () => {
    const offenders = declarations()
      .filter((d) => !EXEMPT_PROFILES.has(d.profile))
      .filter((d) => defaultsToDebug(d.level))
      .map((d) => `${d.file} [profile=${d.profile}] ch.batbern: ${d.level}`);

    expect(offenders).toEqual([]);
  });

  test('should_governTheLevelFromApplicationYaml_when_logbackAlsoExists', () => {
    // The specific trap: a <logger name="ch.batbern"> element in logback-spring.xml is INERT,
    // because Spring applies logging.level.* afterwards. Leaving one there makes the wrong file
    // look authoritative and is how the first fix silently failed.
    const logback = path.join(ROOT, 'api-gateway', 'src', 'main', 'resources', 'logback-spring.xml');
    const xml = fs.readFileSync(logback, 'utf8');
    const withoutComments = xml.replace(/<!--[\s\S]*?-->/g, ''); // the explanation mentions it
    expect(withoutComments).not.toMatch(/<logger[^>]*name\s*=\s*"ch\.batbern"/);
  });
});
