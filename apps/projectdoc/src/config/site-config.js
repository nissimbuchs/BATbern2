// Load core config
const fs = require('fs');
const yaml = require('js-yaml');
const path = require('path');

let coreConfig = {};
try {
  const coreConfigPath = path.resolve(__dirname, '../../../.bmad-core/core-config.yaml');
  const coreConfigFile = fs.readFileSync(coreConfigPath, 'utf8');
  coreConfig = yaml.load(coreConfigFile);
} catch (error) {
  console.warn('Could not load core config:', error.message);
}

module.exports = {
  siteName: 'BATbern Platform Rewriting with Claude Code',
  siteDescription: 'Comprehensive documentation for the BATbern event management platform development',
  baseUrl: 'https://project.batbern.ch',
  author: 'BATbern Development Team',

  // Source and output directories
  docsPath: '../../docs',
  outputPath: './dist',

  // Core configuration settings
  markdownExploder: coreConfig.markdownExploder || false,

  // Document categories and their configurations
  categories: {
    'prd-enhanced': {
      title: 'Product Requirements (Enhanced)',
      description: 'Detailed product requirements and specifications',
      icon: '📋',
      order: 1,
      files: [
        'prd-enhanced.md',
        'todo.md']
    },
    'architecture': {
      title: 'Architecture',
      description: 'System architecture, coding standards, and technical documentation',
      icon: '🏗️',
      order: 2,
      folder: 'architecture'
    },
    'wireframes': {
      title: 'Wireframes & UI Design',
      description: 'User interface wireframes and design specifications',
      icon: '🎨',
      order: 3,
      folder: 'wireframes'
    },
    'epics': {
      title: 'Development Epics',
      description: 'Development epics with detailed user stories',
      icon: '🚀',
      order: 4,
      folder: 'prd',
      pattern: 'epic-*.md'
    },
    'stories': {
      title: 'User Stories',
      description: 'Individual user stories and detailed implementation guides',
      icon: '📝',
      order: 5,
      folder: 'stories'
    },
    'project-docs': {
      title: 'Project Documentation',
      description: 'General project documentation and guides',
      icon: '📚',
      order: 6,
      files: [
        'front-end-spec.md',
        'test-enhanced-markdown.md',
        'brainstorming-session-results.md'
      ]
    }
  },

  // Navigation settings
  navigation: {
    showBreadcrumbs: true,
    showTableOfContents: true,
    maxTocDepth: 3,
    collapsibleSections: true
  },

  // HTML generation settings
  html: {
    highlightTheme: 'github',
    addLineNumbers: true,
    generateToc: true,
    linkifyHeadings: true
  },

  // Site theme and styling
  theme: {
    primaryColor: '#1565C0',      // BATbern blue
    secondaryColor: '#0D47A1',    // Darker blue
    accentColor: '#FF6F00',       // Orange accent
    backgroundColor: '#FAFAFA',   // Light gray
    textColor: '#212121',         // Dark gray
    linkColor: '#1565C0'          // Primary blue
  }
};