const handlebars = require('handlebars');
const fs = require('fs-extra');
const path = require('path');

class HtmlGenerator {
  constructor(config) {
    this.config = config;
    this.templates = {};
  }

  async initialize() {
    await this.loadTemplates();
    this.registerHelpers();
  }

  async loadTemplates() {
    const templateDir = path.join(__dirname, '../templates');

    try {
      const layoutContent = await fs.readFile(path.join(templateDir, 'layout.html'), 'utf-8');
      this.templates.layout = handlebars.compile(layoutContent);

      const navigationContent = await fs.readFile(path.join(templateDir, 'navigation.html'), 'utf-8');
      this.templates.navigation = handlebars.compile(navigationContent);

      console.log('Templates loaded successfully');
    } catch (error) {
      console.error('Error loading templates:', error);
      throw error;
    }
  }

  registerHelpers() {
    // Format date helper
    handlebars.registerHelper('formatDate', function(date) {
      if (!date) return 'Unknown';
      return new Date(date).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      });
    });

    // Format file size helper
    handlebars.registerHelper('formatSize', function(bytes) {
      if (!bytes) return '0 B';
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(1024));
      return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i];
    });

    // Reading time helper
    handlebars.registerHelper('readingTime', function(wordCount) {
      const minutes = Math.ceil(wordCount / 200);
      return minutes === 1 ? '1 minute' : `${minutes} minutes`;
    });

    // Table of contents helper
    handlebars.registerHelper('renderToc', function(toc, options) {
      if (!toc || toc.length === 0) return '';

      function renderTocLevel(items, level = 1) {
        const levelClass = level === 1 ? 'toc-main' : 'toc-sub';
        let html = `<ul class="toc-list ${levelClass}">`;
        items.forEach(item => {
          html += `<li class="toc-item toc-level-${item.level}">`;
          html += `<a href="#${item.id}" class="toc-link">${item.text}</a>`;

          if (item.children && item.children.length > 0) {
            html += renderTocLevel(item.children, level + 1);
          }

          html += '</li>';
        });
        html += '</ul>';
        return html;
      }

      return new handlebars.SafeString(renderTocLevel(toc));
    });

    // Breadcrumb helper
    handlebars.registerHelper('renderBreadcrumbs', function(breadcrumbs) {
      if (!breadcrumbs || breadcrumbs.length === 0) return '';

      let html = '<nav class="breadcrumbs" aria-label="Breadcrumb"><ol class="breadcrumb-list">';

      breadcrumbs.forEach((crumb, index) => {
        const isLast = index === breadcrumbs.length - 1;
        html += `<li class="breadcrumb-item ${isLast ? 'active' : ''}">`;

        if (isLast) {
          html += `<span class="breadcrumb-text">${crumb.title}</span>`;
        } else {
          html += `<a href="${crumb.url}" class="breadcrumb-link">${crumb.title}</a>`;
        }

        html += '</li>';

        if (!isLast) {
          html += '<li class="breadcrumb-separator" aria-hidden="true">/</li>';
        }
      });

      html += '</ol></nav>';
      return new handlebars.SafeString(html);
    });

    // Category icon helper
    handlebars.registerHelper('categoryIcon', function(categoryKey) {
      const category = this.config?.categories?.[categoryKey];
      return category ? category.icon : '📄';
    });

    // Conditional helper
    handlebars.registerHelper('if_eq', function(a, b, options) {
      if (a === b) {
        return options.fn(this);
      }
      return options.inverse(this);
    });

    // Epic number helper
    handlebars.registerHelper('epicNumber', function(epicInfo) {
      return epicInfo ? `Epic ${epicInfo.number}` : '';
    });
  }

  async generatePage(pageData) {
    const {
      metadata,
      content,
      tableOfContents,
      category,
      navigation,
      breadcrumbs
    } = pageData;

    try {
      const pageHtml = this.templates.layout({
        title: metadata.title,
        description: metadata.description,
        content,
        metadata,
        tableOfContents,
        category,
        navigation,
        breadcrumbs,
        config: this.config,
        siteConfig: this.config,
        buildTime: new Date().toISOString()
      });

      return pageHtml;
    } catch (error) {
      console.error('Error generating page HTML:', error);
      throw error;
    }
  }

  async generateIndexPage(categories, allDocuments) {
    const indexData = {
      title: this.config.siteName,
      description: this.config.siteDescription,
      categories: this.buildCategoryData(categories, allDocuments),
      totalDocuments: allDocuments.length,
      lastUpdated: this.getLastUpdatedDate(allDocuments),
      config: this.config,
      siteConfig: this.config,
      buildTime: new Date().toISOString(),
      isIndexPage: true
    };

    // Create a simple index content
    const indexContent = this.generateIndexContent(indexData);

    const indexHtml = this.templates.layout({
      ...indexData,
      content: indexContent,
      navigation: this.buildNavigationData(categories, allDocuments),
      breadcrumbs: []
    });

    return indexHtml;
  }

  buildCategoryData(categories, allDocuments) {
    const result = {};

    Object.entries(this.config.categories).forEach(([key, categoryConfig]) => {
      const docs = allDocuments.filter(doc => doc.category === key);

      result[key] = {
        ...categoryConfig,
        key,
        documents: docs,
        documentCount: docs.length,
        lastUpdated: this.getLastUpdatedDate(docs)
      };
    });

    return result;
  }

  buildNavigationData(categories, allDocuments) {
    const navigation = {};

    Object.entries(this.config.categories).forEach(([key, categoryConfig]) => {
      const docs = allDocuments.filter(doc => doc.category === key);

      // Special handling for epics - group stories under them
      if (key === 'epics') {
        // Get all stories
        const stories = allDocuments.filter(doc => doc.category === 'stories');

        // Sort epic documents by epic number
        const sortedDocs = docs.sort((a, b) => {
          if (a.metadata.epicInfo && b.metadata.epicInfo) {
            return a.metadata.epicInfo.number - b.metadata.epicInfo.number;
          }
          return a.metadata.title.localeCompare(b.metadata.title);
        });

        navigation[key] = {
          ...categoryConfig,
          documents: sortedDocs.map(doc => {
            // Find stories that belong to this epic
            const epicNumber = doc.metadata.epicInfo?.number;
            const epicStories = stories.filter(story => {
              // Extract epic number from story filename (e.g., "1.2.story-name.md")
              const storyMatch = story.metadata.fileName.match(/^(\d+)\.(\d+)\./);
              return storyMatch && parseInt(storyMatch[1]) === epicNumber;
            }).sort((a, b) => {
              // Sort stories by story number (1.1, 1.2, etc.)
              const aMatch = a.metadata.fileName.match(/^(\d+)\.(\d+)\./);
              const bMatch = b.metadata.fileName.match(/^(\d+)\.(\d+)\./);
              if (aMatch && bMatch) {
                const aStoryNum = parseInt(aMatch[2]);
                const bStoryNum = parseInt(bMatch[2]);
                return aStoryNum - bStoryNum;
              }
              return a.metadata.title.localeCompare(b.metadata.title);
            });

            return {
              title: doc.metadata.title,
              url: doc.urlPath,
              description: doc.metadata.description,
              lastModified: doc.metadata.lastModified,
              epicInfo: doc.metadata.epicInfo,
              storyCount: doc.metadata.storyCount,
              stories: epicStories.map(story => ({
                title: story.metadata.title,
                url: story.urlPath,
                description: story.metadata.description,
                storyId: story.metadata.fileName.match(/^(\d+\.\d+)\./)?.[1]
              }))
            };
          })
        };
      } else if (key === 'stories') {
        // Skip stories category since they're now grouped under epics
        return;
      } else {
        // Regular handling for other categories
        const sortedDocs = docs.sort((a, b) => a.metadata.title.localeCompare(b.metadata.title));

        navigation[key] = {
          ...categoryConfig,
          documents: sortedDocs.map(doc => ({
            title: doc.metadata.title,
            url: doc.urlPath,
            description: doc.metadata.description,
            lastModified: doc.metadata.lastModified,
            epicInfo: doc.metadata.epicInfo,
            storyCount: doc.metadata.storyCount
          }))
        };
      }
    });

    return navigation;
  }

  generateIndexContent(indexData) {
    let content = `
      <div class="index-hero">
        <h1 class="hero-title">${indexData.title}</h1>
        <p class="hero-description">${indexData.description}</p>
        <div class="hero-stats">
          <div class="stat">
            <span class="stat-number">${indexData.totalDocuments}</span>
            <span class="stat-label">Documents</span>
          </div>
          <div class="stat">
            <span class="stat-number">${Object.keys(indexData.categories).length}</span>
            <span class="stat-label">Categories</span>
          </div>
          <div class="stat">
            <span class="stat-number">${this.getTotalStories(indexData.categories)}</span>
            <span class="stat-label">User Stories</span>
          </div>
        </div>
      </div>

      <div class="categories-grid">
    `;

    // Sort categories by order
    const sortedCategories = Object.entries(indexData.categories)
      .sort(([,a], [,b]) => a.order - b.order);

    sortedCategories.forEach(([key, category]) => {
      content += `
        <div class="category-card" data-category="${key}">
          <div class="category-header">
            <span class="category-icon">${category.icon}</span>
            <h2 class="category-title">${category.title}</h2>
          </div>
          <p class="category-description">${category.description}</p>
          <div class="category-meta">
            <span class="document-count">${category.documentCount} document${category.documentCount !== 1 ? 's' : ''}</span>
            ${category.lastUpdated ? `<span class="last-updated">Updated ${this.formatRelativeDate(category.lastUpdated)}</span>` : ''}
          </div>
          <div class="category-documents">
      `;

      category.documents.slice(0, 5).forEach(doc => {
        content += `
          <a href="${doc.urlPath}" class="document-link">
            <span class="document-title">${doc.metadata.title}</span>
            ${doc.metadata.epicInfo ? `<span class="epic-badge">Epic ${doc.metadata.epicInfo.number}</span>` : ''}
          </a>
        `;
      });

      if (category.documents.length > 5) {
        content += `<div class="more-documents">+${category.documents.length - 5} more documents</div>`;
      }

      content += `
          </div>
        </div>
      `;
    });

    content += `
      </div>
    `;

    return content;
  }

  getTotalStories(categories) {
    return Object.values(categories).reduce((total, category) => {
      return total + category.documents.reduce((sum, doc) => sum + (doc.metadata.storyCount || 0), 0);
    }, 0);
  }

  formatRelativeDate(date) {
    const now = new Date();
    const diffTime = Math.abs(now - new Date(date));
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 1) return 'yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    if (diffDays < 30) return `${Math.ceil(diffDays / 7)} weeks ago`;
    return `${Math.ceil(diffDays / 30)} months ago`;
  }

  getLastUpdatedDate(documents) {
    if (!documents || documents.length === 0) return null;

    return documents.reduce((latest, doc) => {
      const docDate = new Date(doc.metadata.lastModified);
      return !latest || docDate > latest ? docDate : latest;
    }, null);
  }
}

module.exports = HtmlGenerator;