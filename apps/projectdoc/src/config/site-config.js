module.exports = {
  siteName: 'BATbern Platform Rewriting with Claude Code',
  siteDescription: 'Comprehensive documentation for the BATbern event management platform development',
  baseUrl: 'https://project.batbern.ch',
  author: 'BATbern Development Team',

  // Source and output directories
  docsPath: '../../docs',
  outputPath: './dist',

  // Document categories and their configurations
  categories: {
    'prd-enhanced': {
      title: 'Product Requirements (Enhanced)',
      description: 'Detailed product requirements and specifications',
      icon: '📋',
      order: 1,
      files: ['prd-enhanced.md']
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
    'project-docs': {
      title: 'Project Documentation',
      description: 'General project documentation and guides',
      icon: '📚',
      order: 5,
      files: [
        'prd.md',
        'front-end-spec.md',
        'todo.md',
        'test-enhanced-markdown.md',
        'brainstorming-session-results.md',
        'brownfield-architecture.md'
      ]
    },
    'aws-setup': {
      title: 'AWS & Infrastructure',
      description: 'AWS setup guides and infrastructure documentation',
      icon: '☁️',
      order: 6,
      files: [
        'aws-setup-guide.md',
        'aws-README.md',
        'claude-aws-setup.md',
        'hostpoint-dns-poc.md'
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