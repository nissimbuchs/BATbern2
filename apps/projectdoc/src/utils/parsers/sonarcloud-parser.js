import fetch from 'node-fetch';

/**
 * Parser for SonarCloud quality metrics
 * Fetches complexity, duplication, tech debt, and quality ratings via the SonarCloud API.
 * Requires SONAR_TOKEN environment variable. Returns null gracefully if token is absent.
 */
export class SonarcloudParser {
  /**
   * Metric keys to fetch from SonarCloud
   */
  static METRIC_KEYS = [
    'ncloc',
    'complexity',
    'cognitive_complexity',
    'duplicated_lines_density',
    'duplicated_lines',
    'duplicated_blocks',
    'sqale_index',
    'sqale_debt_ratio',
    'code_smells',
    'reliability_rating',
    'security_rating',
    'sqale_rating',
    'coverage',
    'bugs',
    'vulnerabilities',
    'security_hotspots'
  ].join(',');

  /**
   * Fetch all quality metrics for the project from SonarCloud.
   * @param {Object} config - sonarcloud config section from reports-config.js
   * @returns {Promise<Object|null>} Formatted metrics object, or null if unavailable
   */
  static async fetchMetrics(config) {
    if (!config || !config.enabled) {
      return null;
    }

    const token = process.env.SONAR_TOKEN;
    if (!token) {
      console.warn('SonarCloud: SONAR_TOKEN not set, skipping metrics fetch');
      return null;
    }

    const { apiUrl, projectKey } = config;
    if (!projectKey) {
      console.warn('SonarCloud: projectKey not configured');
      return null;
    }

    const url = `${apiUrl}/measures/component?component=${encodeURIComponent(projectKey)}&metricKeys=${this.METRIC_KEYS}`;

    try {
      console.log(`  Fetching SonarCloud metrics for ${projectKey}...`);
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json'
        },
        timeout: 15000
      });

      if (!response.ok) {
        console.warn(`SonarCloud API returned ${response.status}: ${response.statusText}`);
        return null;
      }

      const data = await response.json();
      return this.formatMetrics(data, config);
    } catch (error) {
      console.warn(`SonarCloud fetch failed: ${error.message}`);
      return null;
    }
  }

  /**
   * Format raw SonarCloud API response into a clean metrics object.
   */
  static formatMetrics(apiResponse, config) {
    const measures = apiResponse?.component?.measures || [];
    const get = (key) => {
      const m = measures.find(m => m.metric === key);
      return m ? m.value : null;
    };

    const techDebtMinutes = get('sqale_index') ? parseInt(get('sqale_index'), 10) : null;

    return {
      ncloc: get('ncloc') ? parseInt(get('ncloc'), 10) : null,
      complexity: get('complexity') ? parseInt(get('complexity'), 10) : null,
      cognitiveComplexity: get('cognitive_complexity') ? parseInt(get('cognitive_complexity'), 10) : null,
      duplicationPct: get('duplicated_lines_density') ? parseFloat(get('duplicated_lines_density')) : null,
      duplicatedLines: get('duplicated_lines') ? parseInt(get('duplicated_lines'), 10) : null,
      duplicatedBlocks: get('duplicated_blocks') ? parseInt(get('duplicated_blocks'), 10) : null,
      techDebtMinutes,
      techDebtFormatted: techDebtMinutes !== null ? this.formatDebt(techDebtMinutes) : null,
      sqaleDebtRatio: get('sqale_debt_ratio') ? parseFloat(get('sqale_debt_ratio')) : null,
      codeSmells: get('code_smells') ? parseInt(get('code_smells'), 10) : null,
      bugs: get('bugs') ? parseInt(get('bugs'), 10) : null,
      vulnerabilities: get('vulnerabilities') ? parseInt(get('vulnerabilities'), 10) : null,
      securityHotspots: get('security_hotspots') ? parseInt(get('security_hotspots'), 10) : null,
      reliabilityRating: get('reliability_rating') ? this.formatRating(get('reliability_rating')) : null,
      securityRating: get('security_rating') ? this.formatRating(get('security_rating')) : null,
      maintainabilityRating: get('sqale_rating') ? this.formatRating(get('sqale_rating')) : null,
      coveragePct: get('coverage') ? parseFloat(get('coverage')) : null,
      projectUrl: `${config.apiUrl.replace('/api', '')}/project/overview?id=${encodeURIComponent(config.projectKey)}`,
      fetchedAt: new Date().toISOString()
    };
  }

  /**
   * Convert SonarCloud numeric rating to letter grade.
   * 1 → A, 2 → B, 3 → C, 4 → D, 5 → E
   */
  static formatRating(value) {
    const map = { '1': 'A', '1.0': 'A', '2': 'B', '2.0': 'B', '3': 'C', '3.0': 'C', '4': 'D', '4.0': 'D', '5': 'E', '5.0': 'E' };
    return map[String(value)] || String(value);
  }

  /**
   * Format technical debt from minutes to human-readable string.
   * e.g. 4320 → '3d', 90 → '1h 30min', 45 → '45min'
   */
  static formatDebt(minutes) {
    if (!minutes || minutes === 0) return '0min';
    const days = Math.floor(minutes / 480);  // 8h working day
    const hours = Math.floor((minutes % 480) / 60);
    const mins = minutes % 60;

    if (days > 0 && hours > 0) return `${days}d ${hours}h`;
    if (days > 0) return `${days}d`;
    if (hours > 0 && mins > 0) return `${hours}h ${mins}min`;
    if (hours > 0) return `${hours}h`;
    return `${mins}min`;
  }

  /**
   * Returns CSS class for a rating letter (A=green → E=red)
   */
  static ratingClass(letter) {
    const map = { A: 'rating-a', B: 'rating-b', C: 'rating-c', D: 'rating-d', E: 'rating-e' };
    return map[letter] || 'rating-unknown';
  }
}

export default SonarcloudParser;
