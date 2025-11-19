#!/usr/bin/env node

/**
 * Migrate and merge history files from multiple local BATbern instances
 * into a shared centralized location
 */

import fs from 'fs-extra';
import path from 'path';
import { fileURLToPath } from 'url';
import { glob } from 'glob';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class HistoryMigrator {
  constructor() {
    this.homeDir = process.env.HOME || process.env.USERPROFILE;
    this.targetFile = path.join(this.homeDir, '.batbern', 'projectdoc-history.json');

    // Search for history files in all BATbern project instances
    this.searchPattern = path.join(
      path.dirname(this.homeDir),
      'nissim/dev/bat/*/apps/projectdoc/dist/reports/data/history.json'
    );
  }

  async migrate() {
    console.log('=== BATbern History Migration Tool ===\n');

    // Step 1: Collect all history files
    const historyFiles = await this.findHistoryFiles();

    if (historyFiles.length === 0) {
      console.log('No history files found. Nothing to migrate.');
      return;
    }

    console.log(`Found ${historyFiles.length} history file(s):\n`);

    historyFiles.forEach((file, idx) => {
      console.log(`  ${idx + 1}. ${file.path}`);
      console.log(`     Entries: ${file.entries}, Last updated: ${file.lastUpdated}`);
    });

    // Step 2: Check if target already exists
    if (await fs.pathExists(this.targetFile)) {
      console.log(`\n⚠️  Target file already exists: ${this.targetFile}`);
      const existingData = await fs.readJson(this.targetFile);
      console.log(`   Current entries: ${existingData.totalEntries || 0}`);
      console.log('   This will be merged with discovered histories.\n');

      // Add existing target to the list
      historyFiles.unshift({
        path: this.targetFile,
        entries: existingData.totalEntries || 0,
        lastUpdated: existingData.lastUpdated,
        data: existingData
      });
    }

    // Step 3: Load and merge histories
    console.log('\nMerging history entries...');
    const mergedHistory = await this.mergeHistories(historyFiles);

    console.log(`  Total entries after deduplication: ${mergedHistory.length}`);

    // Step 4: Sort by timestamp (newest first)
    mergedHistory.sort((a, b) =>
      new Date(b.timestamp) - new Date(a.timestamp)
    );

    // Step 5: Limit to 50 entries
    const limitedHistory = mergedHistory.slice(0, 50);

    if (mergedHistory.length > 50) {
      console.log(`  Applied 50-entry limit: ${limitedHistory.length} entries retained`);
    }

    // Step 6: Save to target location
    await this.saveHistory(limitedHistory, historyFiles.map(f => f.path));

    // Step 7: Display summary
    const dateRange = this.getDateRange(limitedHistory);
    console.log(`\n✅ Migration complete!`);
    console.log(`   Target: ${this.targetFile}`);
    console.log(`   Total entries: ${limitedHistory.length}`);
    console.log(`   Date range: ${dateRange.from} to ${dateRange.to}`);
    console.log(`   Branches: ${this.getUniqueBranches(limitedHistory).join(', ')}`);

    console.log(`\n💡 Next steps:`);
    console.log(`   1. All project instances will now use this shared history`);
    console.log(`   2. Run 'npm run build:reports' from any instance to append new builds`);
    console.log(`   3. To reset history: rm ${this.targetFile}`);
  }

  async findHistoryFiles() {
    const files = [];

    try {
      // Use glob to find all history.json files
      const matches = await glob(this.searchPattern, {
        absolute: true,
        windowsPathsNoEscape: true
      });

      for (const filePath of matches) {
        if (await fs.pathExists(filePath)) {
          try {
            const data = await fs.readJson(filePath);
            files.push({
              path: filePath,
              entries: data.totalEntries || 0,
              lastUpdated: data.lastUpdated || 'unknown',
              data: data
            });
          } catch (error) {
            console.warn(`  ⚠️  Failed to read ${filePath}: ${error.message}`);
          }
        }
      }
    } catch (error) {
      console.warn(`  ⚠️  Error searching for history files: ${error.message}`);
    }

    return files;
  }

  async mergeHistories(historyFiles) {
    const allEntries = [];
    const seenKeys = new Set();

    for (const file of historyFiles) {
      const entries = file.data.history || [];

      for (const entry of entries) {
        // Create unique key: timestamp + branch + commit
        // This ensures we don't duplicate the exact same build
        const key = `${entry.timestamp}-${entry.branch || 'unknown'}-${entry.commit || 'unknown'}`;

        // Only add if not seen before (deduplication)
        if (!seenKeys.has(key)) {
          seenKeys.add(key);
          allEntries.push(entry);
        }
      }
    }

    return allEntries;
  }

  async saveHistory(history, sourcePaths) {
    await fs.ensureDir(path.dirname(this.targetFile));

    const data = {
      version: '1.0',
      lastUpdated: new Date().toISOString(),
      totalEntries: history.length,
      migratedFrom: sourcePaths,
      history: history
    };

    await fs.writeJson(this.targetFile, data, { spaces: 2 });
  }

  getDateRange(history) {
    if (history.length === 0) {
      return { from: 'N/A', to: 'N/A' };
    }

    const oldest = history[history.length - 1].timestamp;
    const newest = history[0].timestamp;

    return {
      from: new Date(oldest).toISOString().split('T')[0],
      to: new Date(newest).toISOString().split('T')[0]
    };
  }

  getUniqueBranches(history) {
    const branches = new Set();
    history.forEach(entry => {
      if (entry.branch) {
        branches.add(entry.branch);
      }
    });
    return Array.from(branches).slice(0, 5); // Show first 5 branches
  }
}

// Execute migration
const migrator = new HistoryMigrator();
migrator.migrate()
  .then(() => {
    console.log('\n');
    process.exit(0);
  })
  .catch(error => {
    console.error('\n❌ Migration failed:', error.message);
    console.error(error.stack);
    process.exit(1);
  });
