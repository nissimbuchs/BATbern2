#!/usr/bin/env node

/**
 * Version Extraction Script
 *
 * Automatically extracts technology versions from dependency files and updates docs/versions.json.
 * This ensures architecture documentation stays in sync with actual project dependencies.
 *
 * Usage:
 *   node scripts/update-versions.js              # Update versions.json
 *   node scripts/update-versions.js --check      # Check for drift (CI mode)
 *   node scripts/update-versions.js --dry-run    # Show changes without writing
 *
 * Exit codes:
 *   0 - Success (or no drift in --check mode)
 *   1 - Error or drift detected in --check mode
 */

const fs = require('fs');
const path = require('path');

// File paths
const ROOT_DIR = path.join(__dirname, '..');
// BATBERN_VERSIONS_FILE lets the test suite point the script at a temporary copy. Nothing in
// CI or the hooks sets it; it exists so scripts/update-versions.test.mjs can exercise the real
// script end to end without writing to the checked-in file.
const VERSIONS_FILE = process.env.BATBERN_VERSIONS_FILE
  || path.join(ROOT_DIR, 'docs', 'versions.json');
const FRONTEND_PACKAGE = path.join(ROOT_DIR, 'web-frontend', 'package.json');
const BACKEND_GRADLE = path.join(ROOT_DIR, 'api-gateway', 'build.gradle');
// Epic 13 (SB4) moved the estate-wide pins out of the per-module build files: the Spring
// Boot plugin version now lives once in settings.gradle pluginManagement, and the
// Testcontainers BOM in the ROOT build.gradle. Scanning only api-gateway/build.gradle
// silently produced null for both — which is how docs/versions.json ended up claiming
// "spring-boot": null while the estate ran 4.0.7.
const ROOT_GRADLE = path.join(ROOT_DIR, 'build.gradle');
const SETTINGS_GRADLE = path.join(ROOT_DIR, 'settings.gradle');
const INFRA_PACKAGE = path.join(ROOT_DIR, 'infrastructure', 'package.json');

// Parse command line arguments
const args = process.argv.slice(2);
const isDryRun = args.includes('--dry-run');
const isCheckMode = args.includes('--check');

/**
 * Extract major.minor version from a semver string
 * Examples: "^19.2.0" -> "19.x", "5.3.3" -> "5.x", "~1.40.0" -> "1.x"
 */
function extractVersion(versionString) {
  if (!versionString) return null;

  // Remove semver prefixes (^, ~, >=, etc.)
  const cleaned = versionString.replace(/^[\^~>=<]+/, '');

  // Extract major.minor
  const match = cleaned.match(/^(\d+)\.(\d+)/);
  if (!match) return null;

  const major = match[1];

  // For major version 0, include minor (0.5.x vs 0.6.x matters)
  // For major version >= 1, use major only (5.x)
  if (major === '0') {
    const minor = match[2];
    return `${major}.${minor}`;
  }

  return `${major}.x`;
}

/**
 * Extract version from Gradle plugin declaration
 * Example: "id 'org.springframework.boot' version '3.5.6'" -> "3.x"
 */
function extractGradlePluginVersion(line) {
  const match = line.match(/version\s+['"]([^'"]+)['"]/);
  if (!match) return null;
  return extractVersion(match[1]);
}

/**
 * Extract version from Gradle dependency BOM
 * Example: "implementation platform('software.amazon.awssdk:bom:2.28.0')" -> "2.x"
 */
function extractGradleBomVersion(line) {
  const match = line.match(/:bom:([^'")]+)/);
  if (!match) return null;
  return extractVersion(match[1]);
}

/**
 * Read and parse frontend package.json
 */
function extractFrontendVersions() {
  try {
    const packageJson = JSON.parse(fs.readFileSync(FRONTEND_PACKAGE, 'utf8'));
    const deps = { ...packageJson.dependencies, ...packageJson.devDependencies };

    return {
      typescript: extractVersion(deps.typescript),
      react: extractVersion(deps.react),
      mui: extractVersion(deps['@mui/material']),
      'tanstack-query': extractVersion(deps['@tanstack/react-query']),
      zustand: extractVersion(deps.zustand),
      vite: extractVersion(deps.vite),
      vitest: extractVersion(deps.vitest),
      'react-testing-library': extractVersion(deps['@testing-library/react']),
      playwright: extractVersion(deps['@playwright/test']) || '1.x', // May be in separate package
      eslint: extractVersion(deps.eslint),
      prettier: extractVersion(deps.prettier),
      axios: extractVersion(deps.axios),
      'react-router-dom': extractVersion(deps['react-router-dom']),
      i18next: extractVersion(deps.i18next)
    };
  } catch (error) {
    console.error(`Error reading frontend package.json: ${error.message}`);
    return null;
  }
}

/**
 * Read and parse backend build.gradle
 */
function extractBackendVersions() {
  try {
    // Concatenate every file that can carry a pin. Missing files are tolerated so the
    // script still works from a partial checkout.
    const gradleContent = [BACKEND_GRADLE, ROOT_GRADLE, SETTINGS_GRADLE]
      .map(f => {
        try {
          return fs.readFileSync(f, 'utf8');
        } catch {
          return '';
        }
      })
      .join('\n');
    const lines = gradleContent.split('\n');

    const versions = {
      java: '21', // Java version is specified in sourceCompatibility
      'spring-boot': null,
      junit: null,
      testcontainers: null,
      gradle: '8.x', // Would need to read gradle-wrapper.properties for exact version
      mockito: null,
      'aws-sdk': null,
      openapi: '3.x'
    };

    lines.forEach(line => {
      // Spring Boot plugin version
      if (line.includes("id 'org.springframework.boot'")) {
        versions['spring-boot'] = extractGradlePluginVersion(line);
      }

      // AWS SDK BOM version
      if (line.includes('software.amazon.awssdk:bom')) {
        versions['aws-sdk'] = extractGradleBomVersion(line);
      }

      // JUnit version
      if (line.includes('org.junit.jupiter:junit-jupiter:')) {
        const match = line.match(/:([0-9.]+)['"]?\s*$/);
        if (match) versions.junit = extractVersion(match[1]);
      }

      // Testcontainers version. Since Epic 13 the version comes from the BOM
      // (org.testcontainers:testcontainers-bom:x.y.z) rather than a direct module
      // coordinate, so match both spellings.
      if (
        line.includes('org.testcontainers:testcontainers:') ||
        line.includes('org.testcontainers:testcontainers-bom:')
      ) {
        const match = line.match(/:([0-9.]+)['"]?\s*$/);
        if (match) versions.testcontainers = extractVersion(match[1]);
      }

      // Mockito version
      if (line.includes('org.mockito:mockito-junit-jupiter:')) {
        const match = line.match(/:([0-9.]+)['"]?\s*$/);
        if (match) versions.mockito = extractVersion(match[1]);
      }
    });

    return versions;
  } catch (error) {
    console.error(`Error reading backend build.gradle: ${error.message}`);
    return null;
  }
}

/**
 * Read and parse infrastructure package.json
 */
function extractInfrastructureVersions() {
  try {
    const packageJson = JSON.parse(fs.readFileSync(INFRA_PACKAGE, 'utf8'));
    const deps = { ...packageJson.dependencies, ...packageJson.devDependencies };

    return {
      'aws-cdk': extractVersion(deps['aws-cdk-lib'] || deps['aws-cdk']),
      typescript: extractVersion(deps.typescript),
      jest: extractVersion(deps.jest),
      node: '20' // Typically specified in .nvmrc or CI config
    };
  } catch (error) {
    console.error(`Error reading infrastructure package.json: ${error.message}`);
    return null;
  }
}

/**
 * Generate updated versions.json content
 */
function generateVersionsJson() {
  const frontendVersions = extractFrontendVersions();
  const backendVersions = extractBackendVersions();
  const infraVersions = extractInfrastructureVersions();

  if (!frontendVersions || !backendVersions || !infraVersions) {
    console.error('Failed to extract versions from one or more dependency files');
    return null;
  }

  return {
    "$schema": "https://json-schema.org/draft-07/schema#",
    "description": "Single source of truth for technology versions in BATbern platform. Regenerate with scripts/update-versions.js after changing a dependency; CI verifies it with --check and never edits it for you.",
    "lastUpdated": new Date().toISOString().split('T')[0],
    "frontend": frontendVersions,
    "backend": backendVersions,
    "infrastructure": infraVersions,
    "database": {
      "postgresql": "15",
      "flyway": "Latest"
    },
    "cache": {
      "caffeine": "3.x"
    },
    "aws": {
      "rds-instance-type": "db.t4g.micro",
      "deployment-model": "Single-AZ",
      "region": "eu-central-1"
    },
    "monitoring": {
      "cloudwatch": "Latest",
      "grafana": "10.x",
      "micrometer": "Latest",
      "logback": "1.x"
    }
  };
}

/**
 * Top-level keys that describe the FILE rather than any dependency's version.
 *
 * #991: `lastUpdated` used to be compared like a version. The generator sets it to today, so on
 * any day later than the file's own date the script reported drift, rewrote the file, and
 * sync-versions.yml committed a diff whose entire content was a date bump. That bot commit
 * landed on the contributor's PR branch, re-triggered every workflow, and stalled the PR behind
 * `action_required` — the same mechanism that has always stalled Dependabot PRs here.
 *
 * It also made `--check` unusable in CI, which is why the check mode this script has shipped
 * with since day one had never been wired up: it would have failed every day, drift or not.
 *
 * `$schema` and `description` are prose about the file for the same reason — an editorial edit
 * to either is not a dependency change and must not fail the check.
 */
const NON_VERSION_KEYS = new Set(['$schema', 'description', 'lastUpdated']);

/**
 * Compare two version objects and return differences
 */
function compareVersions(current, updated) {
  const differences = [];

  function compare(currentObj, updatedObj, path = '') {
    for (const key in updatedObj) {
      const fullPath = path ? `${path}.${key}` : key;

      // Only at the top level: a nested key legitimately called "description" would be data.
      if (path === '' && NON_VERSION_KEYS.has(key)) {
        continue;
      }

      if (typeof updatedObj[key] === 'object' && updatedObj[key] !== null) {
        compare(currentObj[key] || {}, updatedObj[key], fullPath);
      } else if (currentObj[key] !== updatedObj[key]) {
        differences.push({
          path: fullPath,
          current: currentObj[key],
          updated: updatedObj[key]
        });
      }
    }
  }

  compare(current, updated);
  return differences;
}

/**
 * Main execution
 */
function main() {
  console.log('🔍 Extracting versions from dependency files...\n');

  // Generate new versions
  const newVersions = generateVersionsJson();
  if (!newVersions) {
    process.exit(1);
  }

  // Read current versions
  let currentVersions = null;
  try {
    if (fs.existsSync(VERSIONS_FILE)) {
      currentVersions = JSON.parse(fs.readFileSync(VERSIONS_FILE, 'utf8'));
    }
  } catch (error) {
    console.warn(`Warning: Could not read existing versions.json: ${error.message}`);
  }

  // Compare versions
  if (currentVersions) {
    const differences = compareVersions(currentVersions, newVersions);

    if (differences.length === 0) {
      // Deliberately NOT rewriting the file. The only thing that would change is lastUpdated,
      // and a date bump carrying no information is what produced the no-op bot commits (#991).
      console.log('✅ No version changes detected. Documentation is up to date.');
      process.exit(0);
    }

    console.log('📝 Version changes detected:\n');
    differences.forEach(diff => {
      console.log(`  ${diff.path}: ${diff.current || '(new)'} → ${diff.updated}`);
    });
    console.log('');

    if (isCheckMode) {
      console.error('❌ Version drift detected! Run "node scripts/update-versions.js" to update.');
      process.exit(1);
    }
  }

  // Dry run mode
  if (isDryRun) {
    console.log('📋 Dry run mode - would write to versions.json:');
    console.log(JSON.stringify(newVersions, null, 2));
    process.exit(0);
  }

  // Write updated versions
  try {
    fs.writeFileSync(VERSIONS_FILE, JSON.stringify(newVersions, null, 2) + '\n');
    console.log(`✅ Successfully updated ${VERSIONS_FILE}`);
    console.log('\n💡 Tip: Commit this change along with your dependency updates.');
  } catch (error) {
    console.error(`❌ Error writing versions.json: ${error.message}`);
    process.exit(1);
  }
}

// Run the script
main();
