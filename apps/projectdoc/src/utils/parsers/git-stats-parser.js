import { execSync } from 'child_process';

/**
 * Parser for git repository activity statistics.
 * Collects commit velocity, contributor count, and hotspot files using git log.
 * All methods return empty/zero results on any error (graceful degradation).
 */
export class GitStatsParser {
  /**
   * Collect all git stats for the repository.
   * @param {string} baseDir - Repo root directory
   * @returns {Object} Git stats object
   */
  static collectStats(baseDir) {
    console.log('  Collecting git activity stats...');
    const startTime = Date.now();

    try {
      return {
        totalCommits: this.getTotalCommits(baseDir),
        commitsLast30Days: this.getCommitCount(baseDir, 30),
        commitsLast90Days: this.getCommitCount(baseDir, 90),
        contributorCount: this.getContributorCount(baseDir),
        hotspotFiles: this.getHotspotFiles(baseDir),
        weeklyActivity: this.getWeeklyActivity(baseDir, 12),
        firstCommitDate: this.getFirstCommitDate(baseDir),
        lastCommitDate: this.getLastCommitDate(baseDir),
        elapsedMs: Date.now() - startTime
      };
    } catch (error) {
      console.warn(`Git stats collection failed: ${error.message}`);
      return this.emptyStats();
    }
  }

  /**
   * Total number of commits in the repository.
   */
  static getTotalCommits(baseDir) {
    try {
      const output = execSync('git rev-list --count HEAD', {
        encoding: 'utf8', cwd: baseDir, timeout: 10000
      });
      return parseInt(output.trim(), 10) || 0;
    } catch {
      return 0;
    }
  }

  /**
   * Number of commits in the last N days.
   */
  static getCommitCount(baseDir, days) {
    try {
      const output = execSync(`git log --oneline --after="${days} days ago"`, {
        encoding: 'utf8', cwd: baseDir, timeout: 10000
      });
      return output.trim() ? output.trim().split('\n').length : 0;
    } catch {
      return 0;
    }
  }

  /**
   * Number of unique contributors (by author name).
   */
  static getContributorCount(baseDir) {
    try {
      const output = execSync("git log --format='%aN'", {
        encoding: 'utf8', cwd: baseDir, timeout: 10000
      });
      if (!output.trim()) return 0;
      const names = new Set(output.trim().split('\n').map(n => n.trim()).filter(Boolean));
      return names.size;
    } catch {
      return 0;
    }
  }

  /**
   * Top 15 most frequently changed source files (hotspots).
   * Only considers .java, .ts, .tsx, .py, .swift files.
   */
  static getHotspotFiles(baseDir) {
    try {
      const output = execSync(
        "git log --format='' --name-only -- '*.java' '*.ts' '*.tsx' '*.py' '*.swift'",
        { encoding: 'utf8', cwd: baseDir, timeout: 15000 }
      );

      if (!output.trim()) return [];

      const counts = {};
      for (const line of output.split('\n')) {
        const file = line.trim();
        if (file) {
          counts[file] = (counts[file] || 0) + 1;
        }
      }

      return Object.entries(counts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 15)
        .map(([file, changeCount]) => ({ file, changeCount }));
    } catch {
      return [];
    }
  }

  /**
   * Weekly commit activity for the last N weeks.
   * Returns array of { week: 'YYYY-Www', commits: N } sorted oldest first.
   */
  static getWeeklyActivity(baseDir, weeks) {
    try {
      const output = execSync(
        `git log --after="${weeks * 7} days ago" --format="%ad" --date=format:"%Y-W%V"`,
        { encoding: 'utf8', cwd: baseDir, timeout: 10000 }
      );

      if (!output.trim()) return this.emptyWeeklyActivity(weeks);

      const counts = {};
      for (const line of output.split('\n')) {
        const week = line.trim();
        if (week) counts[week] = (counts[week] || 0) + 1;
      }

      // Fill in all weeks (including zero-commit weeks)
      const result = [];
      const now = new Date();
      for (let i = weeks - 1; i >= 0; i--) {
        const date = new Date(now);
        date.setDate(date.getDate() - i * 7);
        const week = this.getISOWeekLabel(date);
        result.push({ week, commits: counts[week] || 0 });
      }
      return result;
    } catch {
      return this.emptyWeeklyActivity(weeks);
    }
  }

  /**
   * Date of the first commit in the repository.
   */
  static getFirstCommitDate(baseDir) {
    try {
      return execSync('git log --reverse --format="%ad" --date=short | head -1', {
        encoding: 'utf8', cwd: baseDir, timeout: 10000, shell: true
      }).trim() || null;
    } catch {
      return null;
    }
  }

  /**
   * Date of the most recent commit.
   */
  static getLastCommitDate(baseDir) {
    try {
      return execSync('git log -1 --format="%ad" --date=short', {
        encoding: 'utf8', cwd: baseDir, timeout: 5000
      }).trim() || null;
    } catch {
      return null;
    }
  }

  // ---- Private helpers ----

  static getISOWeekLabel(date) {
    const d = new Date(date);
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7));
    const week1 = new Date(d.getFullYear(), 0, 4);
    const weekNum = 1 + Math.round(((d - week1) / 86400000 - 3 + (week1.getDay() + 6) % 7) / 7);
    return `${d.getFullYear()}-W${String(weekNum).padStart(2, '0')}`;
  }

  static emptyWeeklyActivity(weeks) {
    const result = [];
    const now = new Date();
    for (let i = weeks - 1; i >= 0; i--) {
      const date = new Date(now);
      date.setDate(date.getDate() - i * 7);
      result.push({ week: this.getISOWeekLabel(date), commits: 0 });
    }
    return result;
  }

  static emptyStats() {
    return {
      totalCommits: 0,
      commitsLast30Days: 0,
      commitsLast90Days: 0,
      contributorCount: 0,
      hotspotFiles: [],
      weeklyActivity: this.emptyWeeklyActivity(12),
      firstCommitDate: null,
      lastCommitDate: null,
      elapsedMs: 0
    };
  }
}

export default GitStatsParser;
