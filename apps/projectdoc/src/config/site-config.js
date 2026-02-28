export default {
  siteName: 'Platform Rewriting with ClaudeCode',
  siteDescription: 'Comprehensive documentation for the BATbern event management platform development',
  baseUrl: 'https://project.batbern.ch',
  author: 'BATbern Development Team',

  // Source and output directories
  docsPath: '../../docs',
  outputPath: './dist',

  // Core configuration settings
  markdownExploder: true,

  // Document categories and their configurations
  categories: {
    'user-guide': {
      title: 'User Guide',
      description: 'Comprehensive organizer documentation and tutorials',
      icon: '📖',
      order: 1,
      folder: 'user-guide',
      pattern: '**/*.md',
      isTopLevel: true
    },
    'prd-enhanced': {
      title: 'Product Requirements (Enhanced)',
      description: 'Detailed product requirements and specifications',
      icon: '📋',
      order: 2,
      files: [
        'prd-enhanced.md',
        'prd-wireframe-alignment-analysis.md',
        'todo.md'],
      isTopLevel: true
    },
    'architecture': {
      title: 'Architecture',
      description: 'System architecture, coding standards, and technical documentation',
      icon: '🏗️',
      order: 3,
      folder: 'architecture',
      isTopLevel: true
    },
    'wireframes': {
      title: 'Wireframes & UI Design',
      description: 'User interface wireframes and design specifications',
      icon: '🎨',
      order: 4,
      folder: 'wireframes',
      isTopLevel: true
    },
    'epics': {
      title: 'Development Epics',
      description: 'Development epics with detailed user stories',
      icon: '🚀',
      order: 5,
      folder: 'prd',
      pattern: 'epic-*.md',
      isTopLevel: true
    },
    'stories': {
      title: 'User Stories',
      description: 'Individual user stories and detailed implementation guides',
      icon: '📝',
      order: 6,
      folder: 'stories',
      isTopLevel: true
    },
    'project-docs': {
      title: 'Project Documentation',
      description: 'General project documentation and guides',
      icon: '📚',
      order: 7,
      files: [
        'front-end-spec.md',
        'test-enhanced-markdown.md',
        'brainstorming-session-results.md'
      ],
      isTopLevel: true
    },
    'api': {
      title: 'API Documentation',
      description: 'OpenAPI specifications for all BATbern platform APIs',
      icon: '🔌',
      order: 8,
      folder: 'api',
      pattern: '*.openapi.yml',
      isTopLevel: true
    },
    'assessment': {
      title: 'Quality Assessment',
      description: 'AI code quality evidence: stability, security, maintainability, and design methodology',
      icon: '🔬',
      order: 9,
      folder: 'assessment',
      pattern: '**/*.md',
      isTopLevel: true
    },
    'plans': {
      title: 'Implementation Plans',
      description: 'Deferred feature plans and technical specifications',
      icon: '🗺️',
      order: 10,
      folder: 'plans',
      pattern: '**/*.md',
      isTopLevel: false
    },
    'reports': {
      title: 'Test & Quality Reports',
      description: 'Comprehensive test results, coverage, security, and quality metrics dashboard',
      icon: '📊',
      order: 11,
      externalLink: '/reports/index.html',
      isExternal: true,
      isTopLevel: true
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

// Top-Level Sections for Homepage (4-box layout)
export const topLevelSections = [
  {
    id: 'user-guide',
    title: 'User Guide',
    description: 'Organizer workflows and tutorials',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 7v14m-9-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4a4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3a3 3 0 0 0-3-3z"/></svg>',
    path: '/user-guide/README.html',
    order: 1
  },
  {
    id: 'documentation',
    title: 'Documentation',
    description: 'Architecture, guides, and technical specs',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m16 6l4 14M12 6v14M8 8v12M4 4v16"/></svg>',
    path: '/documentation/index.html',
    categories: ['architecture', 'prd-enhanced', 'wireframes', 'epics', 'stories', 'project-docs', 'assessment', 'plans'],
    order: 2
  },
  {
    id: 'reports',
    title: 'Reports',
    description: 'Test results, coverage, and quality metrics',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v16a2 2 0 0 0 2 2h16M7 16h8m-8-5h12M7 6h3"/></svg>',
    path: '/reports/index.html',
    order: 3
  },
  {
    id: 'apis',
    title: 'APIs',
    description: 'OpenAPI specifications and endpoints',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m16 18l6-6l-6-6M8 6l-6 6l6 6"/></svg>',
    path: '/api/index.html',
    order: 4
  }
];