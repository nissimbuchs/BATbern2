import fs from 'fs-extra';
import path from 'path';
import { globSync } from 'glob';

/**
 * Parser for project-level counts:
 * - TODO/FIXME/HACK/XXX/NOSONAR comments in source files
 * - Dependency counts per module (Gradle + package.json)
 * - Flyway DB migration counts per service
 */
export class ProjectCountsParser {
  /**
   * Collect all project counts.
   * @param {string} baseDir - Repo root
   * @param {string[]} javaModules - Module paths from reports-config.js sources.java.modules
   * @param {string[]} excludeDirs - Directory names to skip
   * @returns {Promise<Object>} Project counts object
   */
  static async collectAll(baseDir, javaModules, excludeDirs = []) {
    console.log('  Collecting project counts (TODO/deps/migrations)...');
    const startTime = Date.now();

    const [todos, dependencies, migrations] = await Promise.all([
      this.countTodos(baseDir, excludeDirs),
      this.countDependencies(baseDir, javaModules),
      this.countMigrations(baseDir, javaModules)
    ]);

    return {
      todos,
      dependencies,
      migrations,
      totalMigrations: migrations.reduce((sum, m) => sum + m.count, 0),
      totalDependencies: dependencies.reduce((sum, d) => sum + d.total, 0),
      elapsedMs: Date.now() - startTime
    };
  }

  /**
   * Count TODO/FIXME/HACK/XXX/NOSONAR occurrences across all source files.
   * @param {string} baseDir
   * @param {string[]} excludeDirs - Directory names to exclude from glob
   * @returns {Promise<Object>} { total, byType: { TODO, FIXME, HACK, XXX, NOSONAR } }
   */
  static async countTodos(baseDir, excludeDirs = []) {
    const byType = { TODO: 0, FIXME: 0, HACK: 0, XXX: 0, NOSONAR: 0 };

    // Build ignore patterns for glob
    const ignorePatterns = [
      '**/node_modules/**', '**/build/**', '**/dist/**', '**/.gradle/**',
      '**/coverage/**', '**/test-results/**', '**/playwright-report/**',
      ...excludeDirs.map(d => `**/${d}/**`)
    ];

    // Scan Java, TypeScript, Python, and Shell files
    const extensions = ['java', 'ts', 'tsx', 'js', 'py', 'sh'];
    const patterns = extensions.map(ext => `**/*.${ext}`);

    try {
      for (const pattern of patterns) {
        const files = globSync(pattern, {
          cwd: baseDir,
          ignore: ignorePatterns,
          absolute: true
        });

        for (const file of files) {
          try {
            const content = await fs.readFile(file, 'utf8');
            for (const type of Object.keys(byType)) {
              const regex = new RegExp(`\\b${type}\\b`, 'g');
              const matches = content.match(regex);
              if (matches) byType[type] += matches.length;
            }
          } catch {
            // Skip unreadable files
          }
        }
      }
    } catch (error) {
      console.warn(`TODO counting failed: ${error.message}`);
    }

    return {
      total: Object.values(byType).reduce((sum, n) => sum + n, 0),
      byType
    };
  }

  /**
   * Count dependencies per Java module (from build.gradle) and Node projects (from package.json).
   * @param {string} baseDir
   * @param {string[]} javaModules
   * @returns {Promise<Object[]>} Array of { module, prod, dev, total, type }
   */
  static async countDependencies(baseDir, javaModules) {
    const results = [];

    // Java modules — parse build.gradle
    for (const modulePath of javaModules) {
      const gradleFile = path.join(baseDir, modulePath, 'build.gradle');
      if (!await fs.pathExists(gradleFile)) continue;

      try {
        const content = await fs.readFile(gradleFile, 'utf8');
        const prod = this.countGradleDeps(content, ['implementation', 'api', 'compileOnly', 'runtimeOnly']);
        const test = this.countGradleDeps(content, ['testImplementation', 'testRuntimeOnly', 'testCompileOnly']);

        results.push({
          module: modulePath.split('/').pop(),
          modulePath,
          type: 'java',
          prod,
          dev: test,
          total: prod + test
        });
      } catch (error) {
        console.warn(`Failed to parse ${gradleFile}: ${error.message}`);
      }
    }

    // Node projects — parse package.json files
    const packageJsonFiles = globSync('**/package.json', {
      cwd: baseDir,
      ignore: ['**/node_modules/**', '**/build/**', '**/dist/**'],
      absolute: true
    });

    for (const pkgFile of packageJsonFiles) {
      try {
        const pkg = await fs.readJson(pkgFile);
        if (!pkg.name) continue;  // Skip package-lock.json etc.

        const prod = Object.keys(pkg.dependencies || {}).length;
        const dev = Object.keys(pkg.devDependencies || {}).length;

        // Use relative path from baseDir as module name
        const relPath = path.relative(baseDir, path.dirname(pkgFile));
        results.push({
          module: pkg.name,
          modulePath: relPath || '.',
          type: 'node',
          prod,
          dev,
          total: prod + dev
        });
      } catch {
        // Skip malformed package.json
      }
    }

    return results.sort((a, b) => b.total - a.total);
  }

  /**
   * Count Flyway migration files per Java service.
   * @param {string} baseDir
   * @param {string[]} javaModules
   * @returns {Promise<Object[]>} Array of { module, count, latestVersion }
   */
  static async countMigrations(baseDir, javaModules) {
    const results = [];

    for (const modulePath of javaModules) {
      const migrationDir = path.join(baseDir, modulePath, 'src/main/resources/db/migration');
      if (!await fs.pathExists(migrationDir)) continue;

      try {
        const files = globSync('V*.sql', {
          cwd: migrationDir,
          absolute: false
        });

        if (files.length === 0) continue;

        // Extract version numbers and find latest
        const versions = files
          .map(f => {
            const match = f.match(/^V(\d+(?:_\d+)?(?:\.\d+)?)/);
            return match ? match[1] : null;
          })
          .filter(Boolean)
          .sort((a, b) => {
            // Compare version numbers numerically
            const numA = parseFloat(a.replace('_', '.'));
            const numB = parseFloat(b.replace('_', '.'));
            return numA - numB;
          });

        results.push({
          module: modulePath.split('/').pop(),
          modulePath,
          count: files.length,
          latestVersion: versions[versions.length - 1] || null
        });
      } catch (error) {
        console.warn(`Failed to scan migrations for ${modulePath}: ${error.message}`);
      }
    }

    return results.sort((a, b) => b.count - a.count);
  }

  // ---- Private helpers ----

  /**
   * Count dependency declarations matching given configurations in a build.gradle file.
   */
  static countGradleDeps(content, configurations) {
    let count = 0;
    for (const config of configurations) {
      // Match lines like: implementation 'group:artifact:version' or implementation("group:artifact:version")
      const regex = new RegExp(`^\\s*${config}\\s*[\\('"]`, 'gm');
      const matches = content.match(regex);
      if (matches) count += matches.length;
    }
    return count;
  }

  static emptyResult() {
    return {
      todos: { total: 0, byType: { TODO: 0, FIXME: 0, HACK: 0, XXX: 0, NOSONAR: 0 } },
      dependencies: [],
      migrations: [],
      totalMigrations: 0,
      totalDependencies: 0,
      elapsedMs: 0
    };
  }
}

export default ProjectCountsParser;
