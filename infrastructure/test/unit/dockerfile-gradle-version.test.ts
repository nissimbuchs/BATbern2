import * as fs from 'fs';
import * as path from 'path';

/**
 * #1220 item 2: the production images were built with gradle:8.14.5 while tests, CI and local
 * builds ran the wrapper's Gradle 9.x, so what shipped was built by a Gradle that never ran the
 * tests. Every Dockerfile's Gradle image must match the wrapper.
 */
describe('Docker images build with the wrapper Gradle (#1220)', () => {
  const root = path.resolve(__dirname, '../../..');
  const wrapper = fs.readFileSync(
    path.join(root, 'gradle/wrapper/gradle-wrapper.properties'),
    'utf8'
  );
  const wrapperVersion = /gradle-([\d.]+)-(?:bin|all)\.zip/.exec(wrapper)?.[1];

  const dockerfiles = [
    'api-gateway',
    ...fs.readdirSync(path.join(root, 'services')).map((s) => `services/${s}`),
  ]
    .flatMap((dir) => ['Dockerfile', 'Dockerfile.dev'].map((f) => path.join(dir, f)))
    .filter((f) => fs.existsSync(path.join(root, f)));

  test('should_findTheWrapperVersionAndTheDockerfiles', () => {
    expect(wrapperVersion).toMatch(/^9\./);
    expect(dockerfiles.length).toBeGreaterThanOrEqual(8);
  });

  test.each(dockerfiles)('%s uses the wrapper Gradle', (file) => {
    const images = [
      ...fs
        .readFileSync(path.join(root, file), 'utf8')
        .matchAll(/^FROM\s+(?:--platform=\S+\s+)?gradle:([\d.]+)-/gm),
    ];
    for (const [, version] of images) {
      expect(version).toBe(wrapperVersion);
    }
  });

  // Gradle 9 refuses to configure an included project whose directory does not exist; the
  // production images copy only their own module, so each must create the others' directories.
  // Missing this broke every image build on the first CI run of #1328.
  const settings = fs.readFileSync(path.join(root, 'settings.gradle'), 'utf8');
  const projects = [...settings.matchAll(/^include\s+'([^']+)'/gm)].map(([, p]) =>
    p.replace(/:/g, '/')
  );

  test.each(dockerfiles.filter((f) => !f.endsWith('.dev')))(
    '%s creates a directory for every included project',
    (file) => {
      const text = fs.readFileSync(path.join(root, file), 'utf8');
      const mkdir = /RUN mkdir -p ((?:[^\n]*\\\n)*[^\n]*)/.exec(text)?.[1] ?? '';
      expect(projects.length).toBeGreaterThanOrEqual(5);
      for (const project of projects) {
        expect(mkdir.split(/[\s\\]+/)).toContain(project);
      }
    }
  );
});
