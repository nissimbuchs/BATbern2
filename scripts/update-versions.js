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
const VERSIONS_FILE = path.join(ROOT_DIR, 'docs', 'versions.json');
const FRONTEND_PACKAGE = path.join(ROOT_DIR, 'web-frontend', 'package.json');
const BACKEND_GRADLE = path.join(ROOT_DIR, 'api-gateway', 'build.gradle');
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
    const gradleContent = fs.readFileSync(BACKEND_GRADLE, 'utf8');
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

      // Testcontainers version
      if (line.includes('org.testcontainers:testcontainers:')) {
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
    "description": "Single source of truth for technology versions in BATbern platform. This file is automatically updated by scripts/update-versions.js when dependencies change.",
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
 * Compare two version objects and return differences
 */
function compareVersions(current, updated) {
  const differences = [];

  function compare(currentObj, updatedObj, path = '') {
    for (const key in updatedObj) {
      const fullPath = path ? `${path}.${key}` : key;

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
