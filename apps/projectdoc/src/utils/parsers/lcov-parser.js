import fs from 'fs-extra';
import path from 'path';
import glob from 'glob';

/**
 * Parser for LCOV coverage reports (Frontend/JavaScript)
 * Extracts line coverage, function coverage, and branch coverage
 */
export class LcovParser {
  /**
   * Parse an LCOV format coverage file
   * @param {string} filePath - Path to lcov.info file
   * @returns {Promise<Object>} Parsed coverage data
   */
  static async parseFile(filePath) {
    try {
      const content = await fs.readFile(filePath, 'utf8');
      return this.parseLcovContent(content);
    } catch (error) {
      console.error(`Failed to parse LCOV report: ${filePath}`, error.message);
      return null;
    }
  }

  /**
   * Parse LCOV format content
   * @param {string} content - LCOV file content
   * @returns {Object} Parsed coverage data
   */
  static parseLcovContent(content) {
    const lines = content.split('\n');
    const files = [];
    let currentFile = null;

    lines.forEach(line => {
      const trimmed = line.trim();

      if (trimmed.startsWith('SF:')) {
        // Start of new file
        currentFile = {
          path: trimmed.substring(3),
          functions: { found: 0, hit: 0, details: [] },
          lines: { found: 0, hit: 0, details: [] },
          branches: { found: 0, hit: 0, details: [] }
        };
      } else if (trimmed === 'end_of_record') {
        // End of current file
        if (currentFile) {
          files.push(currentFile);
          currentFile = null;
        }
      } else if (currentFile) {
        // Parse coverage data
        if (trimmed.startsWith('FN:')) {
          // Function definition: FN:line,function_name
          const parts = trimmed.substring(3).split(',');
          currentFile.functions.details.push({
            line: parseInt(parts[0], 10),
            name: parts[1] || 'anonymous'
          });
        } else if (trimmed.startsWith('FNDA:')) {
          // Function hit count: FNDA:hit_count,function_name
          const parts = trimmed.substring(5).split(',');
          const hitCount = parseInt(parts[0], 10);
          const fnIndex = currentFile.functions.details.findIndex(
            f => f.name === parts[1]
          );
          if (fnIndex >= 0) {
            currentFile.functions.details[fnIndex].hits = hitCount;
          }
        } else if (trimmed.startsWith('FNF:')) {
          // Functions found
          currentFile.functions.found = parseInt(trimmed.substring(4), 10);
        } else if (trimmed.startsWith('FNH:')) {
          // Functions hit
          currentFile.functions.hit = parseInt(trimmed.substring(4), 10);
        } else if (trimmed.startsWith('DA:')) {
          // Line coverage: DA:line_number,hit_count
          const parts = trimmed.substring(3).split(',');
          currentFile.lines.details.push({
            line: parseInt(parts[0], 10),
            hits: parseInt(parts[1], 10)
          });
        } else if (trimmed.startsWith('LF:')) {
          // Lines found
          currentFile.lines.found = parseInt(trimmed.substring(3), 10);
        } else if (trimmed.startsWith('LH:')) {
          // Lines hit
          currentFile.lines.hit = parseInt(trimmed.substring(3), 10);
        } else if (trimmed.startsWith('BRDA:')) {
          // Branch coverage: BRDA:line,block,branch,taken
          const parts = trimmed.substring(5).split(',');
          currentFile.branches.details.push({
            line: parseInt(parts[0], 10),
            block: parseInt(parts[1], 10),
            branch: parseInt(parts[2], 10),
            taken: parts[3] === '-' ? 0 : parseInt(parts[3], 10)
          });
        } else if (trimmed.startsWith('BRF:')) {
          // Branches found
          currentFile.branches.found = parseInt(trimmed.substring(4), 10);
        } else if (trimmed.startsWith('BRH:')) {
          // Branches hit
          currentFile.branches.hit = parseInt(trimmed.substring(4), 10);
        }
      }
    });

    return {
      type: 'lcov',
      files: files,
      summary: this.calculateSummary(files)
    };
  }

  /**
   * Calculate summary statistics from file coverage data
   * @param {Array} files - Array of file coverage data
   * @returns {Object} Summary statistics
   */
  static calculateSummary(files) {
    const summary = {
      totalFiles: files.length,
      lines: { found: 0, hit: 0, percentage: 0 },
      functions: { found: 0, hit: 0, percentage: 0 },
      branches: { found: 0, hit: 0, percentage: 0 }
    };

    files.forEach(file => {
      summary.lines.found += file.lines.found;
      summary.lines.hit += file.lines.hit;
      summary.functions.found += file.functions.found;
      summary.functions.hit += file.functions.hit;
      summary.branches.found += file.branches.found;
      summary.branches.hit += file.branches.hit;
    });

    // Calculate percentages
    summary.lines.percentage = summary.lines.found > 0
      ? Math.round((summary.lines.hit / summary.lines.found) * 10000) / 100
      : 0;
    summary.functions.percentage = summary.functions.found > 0
      ? Math.round((summary.functions.hit / summary.functions.found) * 10000) / 100
      : 0;
    summary.branches.percentage = summary.branches.found > 0
      ? Math.round((summary.branches.hit / summary.branches.found) * 10000) / 100
      : 0;

    return summary;
  }

  /**
   * Find and parse LCOV reports in a directory tree
   * @param {string} baseDir - Base directory to search
   * @param {string} pattern - Glob pattern for LCOV files
   * @returns {Promise<Array>} Array of parsed reports
   */
  static async findAndParseReports(baseDir, pattern = '**/coverage/lcov.info') {
    const reports = [];
    const files = glob.sync(pattern, { cwd: baseDir, absolute: true });

    for (const file of files) {
      const coverage = await this.parseFile(file);
      if (coverage) {
        const moduleName = this.extractModuleName(file, baseDir);
        reports.push({
          module: moduleName,
          reportPath: path.relative(baseDir, file),
          coverage: coverage
        });
      }
    }

    return reports;
  }

  /**
   * Extract module name from file path
   * @param {string} filePath - Full file path
   * @param {string} baseDir - Base directory
   * @returns {string} Module name
   */
  static extractModuleName(filePath, baseDir) {
    const relativePath = path.relative(baseDir, filePath);
    const parts = relativePath.split(path.sep);

    // Typically: module-name/coverage/lcov.info
    return parts[0] || 'unknown';
  }

  /**
   * Get top uncovered files (sorted by uncovered lines)
   * @param {Object} coverage - Parsed coverage data
   * @param {number} limit - Maximum number of files to return
   * @returns {Array} Top uncovered files
   */
  static getTopUncoveredFiles(coverage, limit = 10) {
    return coverage.files
      .map(file => {
        const uncoveredLines = file.lines.found - file.lines.hit;
        const coveragePercentage = file.lines.found > 0
          ? (file.lines.hit / file.lines.found) * 100
          : 0;

        return {
          path: file.path,
          uncoveredLines,
          totalLines: file.lines.found,
          coveragePercentage: Math.round(coveragePercentage * 100) / 100,
          uncoveredFunctions: file.functions.found - file.functions.hit,
          uncoveredBranches: file.branches.found - file.branches.hit
        };
      })
      .sort((a, b) => b.uncoveredLines - a.uncoveredLines)
      .slice(0, limit);
  }

  /**
   * Calculate coverage by directory
   * @param {Object} coverage - Parsed coverage data
   * @returns {Array} Coverage grouped by directory
   */
  static getCoverageByDirectory(coverage) {
    const dirMap = new Map();

    coverage.files.forEach(file => {
      const dir = path.dirname(file.path);

      if (!dirMap.has(dir)) {
        dirMap.set(dir, {
          path: dir,
          lines: { found: 0, hit: 0 },
          functions: { found: 0, hit: 0 },
          branches: { found: 0, hit: 0 },
          fileCount: 0
        });
      }

      const dirData = dirMap.get(dir);
      dirData.lines.found += file.lines.found;
      dirData.lines.hit += file.lines.hit;
      dirData.functions.found += file.functions.found;
      dirData.functions.hit += file.functions.hit;
      dirData.branches.found += file.branches.found;
      dirData.branches.hit += file.branches.hit;
      dirData.fileCount++;
    });

    return Array.from(dirMap.values()).map(dir => ({
      ...dir,
      lines: {
        ...dir.lines,
        percentage: dir.lines.found > 0
          ? Math.round((dir.lines.hit / dir.lines.found) * 10000) / 100
          : 0
      },
      functions: {
        ...dir.functions,
        percentage: dir.functions.found > 0
          ? Math.round((dir.functions.hit / dir.functions.found) * 10000) / 100
          : 0
      },
      branches: {
        ...dir.branches,
        percentage: dir.branches.found > 0
          ? Math.round((dir.branches.hit / dir.branches.found) * 10000) / 100
          : 0
      }
    }));
  }
}

export default LcovParser;
