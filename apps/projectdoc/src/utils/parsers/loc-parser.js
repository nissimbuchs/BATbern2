import { execSync } from 'child_process';
import fs from 'fs-extra';
import path from 'path';

/**
 * Parser for Lines of Code metrics using cloc
 * Wraps the cloc Perl script (installed as npm package) to count source lines
 * across the polyglot BATbern monorepo.
 */
export class LocParser {
  /**
   * Run cloc on a list of directories and return raw JSON output.
   * Returns empty SUM result on any error (graceful degradation).
   * @param {string[]} dirs - Absolute directory paths
   * @param {Object} options
   * @param {string[]} options.excludeDirs - Directory names to exclude
   * @param {string} [options.matchFile] - Regex: only count files matching this pattern
   * @param {string} [options.notMatchFile] - Regex: exclude files matching this pattern
   * @param {string} baseDir - Working directory (repo root, used to resolve cloc binary)
   * @returns {Object} Raw cloc JSON result
   */
  static runCloc(dirs, options, baseDir) {
    const existingDirs = dirs.filter(d => fs.pathExistsSync(d));
    if (existingDirs.length === 0) {
      return this.emptyClocResult();
    }

    const excludeDirArg = (options.excludeDirs || []).join(',');
    const excludeFlag = excludeDirArg ? `--exclude-dir=${excludeDirArg}` : '';
    const matchFileFlag = options.matchFile ? `--match-f="${options.matchFile}"` : '';
    const notMatchFileFlag = options.notMatchFile ? `--not-match-f="${options.notMatchFile}"` : '';

    // Use local node_modules/.bin/cloc first, fall back to global cloc
    const clocBin = fs.pathExistsSync(path.join(baseDir, 'apps/projectdoc/node_modules/.bin/cloc'))
      ? path.join(baseDir, 'apps/projectdoc/node_modules/.bin/cloc')
      : 'cloc';

    const dirArgs = existingDirs.map(d => `"${d}"`).join(' ');
    const cmd = [clocBin, '--json', excludeFlag, matchFileFlag, notMatchFileFlag, dirArgs]
      .filter(Boolean)
      .join(' ');

    try {
      const output = execSync(cmd, {
        encoding: 'utf8',
        cwd: baseDir,
        timeout: 30000,
        stdio: ['pipe', 'pipe', 'pipe']
      });
      return JSON.parse(output);
    } catch (error) {
      if (error.stdout) {
        try {
          return JSON.parse(error.stdout);
        } catch {
          // fall through
        }
      }
      console.warn(`cloc failed for dirs [${existingDirs.join(', ')}]: ${error.message}`);
      return this.emptyClocResult();
    }
  }

  /**
   * Analyze a zone (e.g. "Backend Services") by running cloc separately
   * on production dirs and test dirs, then combining results.
   * @param {string} id - Zone identifier
   * @param {string} label - Human-readable zone label
   * @param {string[]} prodDirs - Absolute paths for production code
   * @param {string[]} testDirs - Absolute paths for test code
   * @param {string[]} excludeDirs - Dir names to exclude
   * @param {string} baseDir - Repo root
   * @param {Object} [fileFilters] - Optional { matchFileProd, notMatchFileProd, matchFileTest }
   * @returns {Object} Zone metrics object
   */
  static analyzeZone(id, label, prodDirs, testDirs, excludeDirs, baseDir, fileFilters = {}) {
    const prodResult = this.runCloc(prodDirs, {
      excludeDirs,
      notMatchFile: fileFilters.notMatchFileProd
    }, baseDir);

    const testResult = this.runCloc(testDirs, {
      excludeDirs,
      matchFile: fileFilters.matchFileTest
    }, baseDir);

    // Also capture test files that are inline in the prod dirs (e.g. *.test.tsx in web-frontend/src)
    let inlineTestResult = this.emptyClocResult();
    if (fileFilters.notMatchFileProd && prodDirs.length > 0) {
      inlineTestResult = this.runCloc(prodDirs, {
        excludeDirs,
        matchFile: fileFilters.notMatchFileProd  // match what we excluded from prod
      }, baseDir);
    }

    const prod = this.summarizeClocResult(prodResult);
    const test = this.summarizeClocResult(testResult);
    const inlineTest = this.summarizeClocResult(inlineTestResult);

    // Merge test + inlineTest
    const mergedTest = {
      code: test.code + inlineTest.code,
      blank: test.blank + inlineTest.blank,
      comment: test.comment + inlineTest.comment,
      nFiles: test.nFiles + inlineTest.nFiles,
      byLanguage: this.mergeLanguageMaps(test.byLanguage, inlineTest.byLanguage)
    };

    const total = {
      code: prod.code + mergedTest.code,
      blank: prod.blank + mergedTest.blank,
      comment: prod.comment + mergedTest.comment,
      nFiles: prod.nFiles + mergedTest.nFiles
    };

    return {
      id,
      label,
      prod,
      test: mergedTest,
      total,
      testToCodeRatio: prod.code > 0 ? Math.round((mergedTest.code / prod.code) * 100) / 100 : 0
    };
  }

  /**
   * Build the full LOC report from an array of zone results.
   * @param {Object[]} zones - Zone objects from analyzeZone()
   * @param {string} clocVersion - Version string from cloc
   * @param {number} elapsedMs - Time taken
   * @returns {Object} Full LOC report
   */
  static buildReport(zones, clocVersion, elapsedMs) {
    // Separate regular zones from generated-code zones so generated LOC is tracked separately
    const regularZones = zones.filter(z => !z.isGenerated);
    const generatedZones = zones.filter(z => z.isGenerated);

    // Merge all language data across zones
    const languageMap = {};
    let totalProd = { code: 0, blank: 0, comment: 0, nFiles: 0 };
    let totalTest = { code: 0, blank: 0, comment: 0, nFiles: 0 };

    for (const zone of regularZones) {
      // Accumulate totals
      totalProd.code += zone.prod.code;
      totalProd.blank += zone.prod.blank;
      totalProd.comment += zone.prod.comment;
      totalProd.nFiles += zone.prod.nFiles;

      totalTest.code += zone.test.code;
      totalTest.blank += zone.test.blank;
      totalTest.comment += zone.test.comment;
      totalTest.nFiles += zone.test.nFiles;

      // Accumulate language totals from both prod and test
      this.accumulateLanguages(languageMap, zone.prod.byLanguage);
      this.accumulateLanguages(languageMap, zone.test.byLanguage);
    }

    // Tally generated code separately (excluded from prodLoc / testLoc totals)
    let totalGeneratedCode = 0;
    let totalGeneratedFiles = 0;
    for (const zone of generatedZones) {
      totalGeneratedCode += zone.prod.code + zone.test.code;
      totalGeneratedFiles += zone.prod.nFiles + zone.test.nFiles;
    }

    const totalCode = totalProd.code + totalTest.code;
    const totalComment = totalProd.comment + totalTest.comment;
    const totalBlank = totalProd.blank + totalTest.blank;
    const totalFiles = totalProd.nFiles + totalTest.nFiles;

    // Build sorted byLanguage array
    const byLanguage = Object.entries(languageMap)
      .map(([language, stats]) => ({
        language,
        code: stats.code,
        blank: stats.blank,
        comment: stats.comment,
        nFiles: stats.nFiles,
        commentRatio: (stats.code + stats.comment) > 0
          ? Math.round((stats.comment / (stats.code + stats.comment)) * 100) / 100
          : 0,
        percentage: totalCode > 0 ? Math.round((stats.code / totalCode) * 1000) / 10 : 0
      }))
      .sort((a, b) => b.code - a.code);

    return {
      summary: {
        totalLoc: totalCode,
        prodLoc: totalProd.code,
        testLoc: totalTest.code,
        totalFiles,
        prodFiles: totalProd.nFiles,
        testFiles: totalTest.nFiles,
        testToCodeRatio: totalProd.code > 0
          ? Math.round((totalTest.code / totalProd.code) * 100) / 100
          : 0,
        commentRatio: (totalCode + totalComment) > 0
          ? Math.round((totalComment / (totalCode + totalComment)) * 100) / 100
          : 0,
        totalBlank,
        totalComment,
        generatedLoc: totalGeneratedCode,
        generatedFiles: totalGeneratedFiles
      },
      byLanguage,
      zones,               // includes both regular and generated zones
      generatedAt: new Date().toISOString(),
      clocVersion: clocVersion || 'unknown',
      elapsedMs: elapsedMs || 0
    };
  }

  /**
   * Extract cloc version from running cloc --version
   */
  static getClocVersion(baseDir) {
    try {
      const clocBin = fs.pathExistsSync(path.join(baseDir, 'apps/projectdoc/node_modules/.bin/cloc'))
        ? path.join(baseDir, 'apps/projectdoc/node_modules/.bin/cloc')
        : 'cloc';
      return execSync(`${clocBin} --version`, { encoding: 'utf8', timeout: 5000 }).trim();
    } catch {
      return 'unknown';
    }
  }

  // ---- Private helpers ----

  static summarizeClocResult(clocResult) {
    const sum = clocResult.SUM || { code: 0, blank: 0, comment: 0, nFiles: 0 };
    const byLanguage = {};
    for (const [key, val] of Object.entries(clocResult)) {
      if (key === 'header' || key === 'SUM') continue;
      byLanguage[key] = {
        code: val.code || 0,
        blank: val.blank || 0,
        comment: val.comment || 0,
        nFiles: val.nFiles || 0
      };
    }
    return {
      code: sum.code || 0,
      blank: sum.blank || 0,
      comment: sum.comment || 0,
      nFiles: sum.nFiles || 0,
      byLanguage
    };
  }

  static mergeLanguageMaps(mapA, mapB) {
    const merged = { ...mapA };
    for (const [lang, stats] of Object.entries(mapB)) {
      if (merged[lang]) {
        merged[lang] = {
          code: merged[lang].code + stats.code,
          blank: merged[lang].blank + stats.blank,
          comment: merged[lang].comment + stats.comment,
          nFiles: merged[lang].nFiles + stats.nFiles
        };
      } else {
        merged[lang] = { ...stats };
      }
    }
    return merged;
  }

  static accumulateLanguages(languageMap, byLanguage) {
    for (const [lang, stats] of Object.entries(byLanguage)) {
      if (!languageMap[lang]) {
        languageMap[lang] = { code: 0, blank: 0, comment: 0, nFiles: 0 };
      }
      languageMap[lang].code += stats.code;
      languageMap[lang].blank += stats.blank;
      languageMap[lang].comment += stats.comment;
      languageMap[lang].nFiles += stats.nFiles;
    }
  }

  static emptyClocResult() {
    return { SUM: { code: 0, blank: 0, comment: 0, nFiles: 0 }, header: {} };
  }

  static emptyReport() {
    return {
      summary: {
        totalLoc: 0, prodLoc: 0, testLoc: 0, totalFiles: 0,
        prodFiles: 0, testFiles: 0, testToCodeRatio: 0,
        commentRatio: 0, totalBlank: 0, totalComment: 0
      },
      byLanguage: [],
      zones: [],
      generatedAt: new Date().toISOString(),
      clocVersion: 'unknown',
      elapsedMs: 0
    };
  }
}

export default LocParser;
