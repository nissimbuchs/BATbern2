const fs = require('fs-extra');
const yaml = require('js-yaml');
const path = require('path');

/**
 * OpenAPI Processor
 * Processes OpenAPI specifications and extracts metadata
 */
class OpenApiProcessor {
  constructor(config) {
    this.config = config;
  }

  /**
   * Process an OpenAPI specification file
   * @param {string} filePath - Path to the OpenAPI YAML file
   * @param {string} relativePath - Relative path for URL generation
   * @returns {Promise<object>} Processed OpenAPI data
   */
  async processFile(filePath, relativePath) {
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      const spec = yaml.load(content);

      const metadata = this.extractMetadata(spec, filePath);
      const stats = this.generateStats(spec);

      return {
        metadata,
        stats,
        spec,
        rawContent: content
      };
    } catch (error) {
      console.error(`Error processing OpenAPI file ${filePath}:`, error.message);
      throw error;
    }
  }

  /**
   * Extract metadata from OpenAPI specification
   * @param {object} spec - Parsed OpenAPI specification
   * @param {string} filePath - File path for stats
   * @returns {object} Metadata object
   */
  extractMetadata(spec, filePath) {
    const info = spec.info || {};
    const servers = spec.servers || [];

    // Get file stats
    const stats = fs.statSync(filePath);

    return {
      title: info.title || 'Untitled API',
      description: info.description || '',
      version: info.version || '1.0.0',
      contact: info.contact || {},
      license: info.license || {},
      servers: servers.map(s => ({
        url: s.url,
        description: s.description
      })),
      lastModified: stats.mtime,
      size: stats.size,
      openApiVersion: spec.openapi || spec.swagger || '3.0.0'
    };
  }

  /**
   * Generate statistics from OpenAPI specification
   * @param {object} spec - Parsed OpenAPI specification
   * @returns {object} Statistics object
   */
  generateStats(spec) {
    const paths = spec.paths || {};
    const schemas = (spec.components && spec.components.schemas) || {};
    const securitySchemes = (spec.components && spec.components.securitySchemes) || {};

    // Count endpoints by HTTP method
    const methods = {};
    const tags = new Set();
    let totalEndpoints = 0;

    Object.entries(paths).forEach(([pathName, pathItem]) => {
      Object.keys(pathItem).forEach(method => {
        if (['get', 'post', 'put', 'patch', 'delete', 'options', 'head'].includes(method)) {
          methods[method] = (methods[method] || 0) + 1;
          totalEndpoints++;

          // Collect tags
          const operation = pathItem[method];
          if (operation.tags) {
            operation.tags.forEach(tag => tags.add(tag));
          }
        }
      });
    });

    return {
      totalEndpoints,
      methods,
      totalPaths: Object.keys(paths).length,
      totalSchemas: Object.keys(schemas).length,
      totalSecuritySchemes: Object.keys(securitySchemes).length,
      tags: Array.from(tags),
      hasAuthentication: Object.keys(securitySchemes).length > 0
    };
  }

  /**
   * Validate OpenAPI specification
   * @param {object} spec - Parsed OpenAPI specification
   * @returns {object} Validation result
   */
  validate(spec) {
    const errors = [];
    const warnings = [];

    // Basic validation
    if (!spec.openapi && !spec.swagger) {
      errors.push('Missing OpenAPI or Swagger version field');
    }

    if (!spec.info) {
      errors.push('Missing info object');
    } else {
      if (!spec.info.title) warnings.push('Missing API title');
      if (!spec.info.version) warnings.push('Missing API version');
    }

    if (!spec.paths || Object.keys(spec.paths).length === 0) {
      warnings.push('No paths defined in the specification');
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings
    };
  }

  /**
   * Generate API summary for index page
   * @param {object} spec - Parsed OpenAPI specification
   * @param {object} metadata - Extracted metadata
   * @param {object} stats - Generated statistics
   * @returns {object} API summary
   */
  generateSummary(spec, metadata, stats) {
    return {
      title: metadata.title,
      description: metadata.description,
      version: metadata.version,
      endpoints: stats.totalEndpoints,
      schemas: stats.totalSchemas,
      tags: stats.tags,
      baseUrl: metadata.servers.length > 0 ? metadata.servers[0].url : null,
      hasAuth: stats.hasAuthentication
    };
  }
}

module.exports = OpenApiProcessor;
