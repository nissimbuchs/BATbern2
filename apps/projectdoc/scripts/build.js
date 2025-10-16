#!/usr/bin/env node

import fs from 'fs-extra';
import path from 'path';
import { glob } from 'glob';
import MarkdownProcessor from '../src/builders/markdown-processor.js';
import HtmlGenerator from '../src/builders/html-generator.js';
import AssetProcessor from '../src/builders/asset-processor.js';
import OpenApiProcessor from '../src/builders/openapi-processor.js';
import config from '../src/config/site-config.js';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class DocumentationBuilder {
  constructor() {
    this.config = config;
    this.markdownProcessor = new MarkdownProcessor(config);
    this.htmlGenerator = new HtmlGenerator(config);
    this.assetProcessor = new AssetProcessor(config);
    this.openapiProcessor = new OpenApiProcessor(config);
    this.documents = [];
    this.apiDocuments = [];
    this.categories = {};
  }

  async build() {
    console.log('🏔️ BATbern Documentation Builder');
    console.log('================================\n');

    try {
      // Initialize components
      await this.initialize();

      // Clean and prepare output directory
      await this.prepareOutputDirectory();

      // Discover and process documents
      await this.discoverDocuments();
      await this.processDocuments();

      // Process assets
      await this.processAssets();

      // Generate HTML pages
      await this.generatePages();

      // Copy static files
      await this.copyStaticFiles();

      // Generate build manifest
      await this.generateManifest();

      console.log('\n✅ Build completed successfully!');
      console.log(`📊 Generated ${this.documents.length} pages`);
      console.log(`📁 Output directory: ${path.resolve(this.config.outputPath)}`);

    } catch (error) {
      console.error('\n❌ Build failed:', error.message);
      if (process.env.NODE_ENV === 'development') {
        console.error(error.stack);
      }
      process.exit(1);
    }
  }

  async initialize() {
    console.log('🔧 Initializing components...');
    await this.htmlGenerator.initialize();
    console.log('✅ Components initialized\n');
  }

  async prepareOutputDirectory() {
    console.log('📁 Preparing output directory...');

    const outputPath = path.resolve(this.config.outputPath);

    // Clean existing output BUT preserve reports directory
    if (await fs.pathExists(outputPath)) {
      const reportsDir = path.join(outputPath, 'reports');
      let reportsBackup = null;

      // Backup reports directory if it exists - move to temp location OUTSIDE output path
      if (await fs.pathExists(reportsDir)) {
        const tempDir = path.join(__dirname, '.reports-temp');
        await fs.move(reportsDir, tempDir, { overwrite: true });
        reportsBackup = tempDir;
      }

      // Clean the directory
      await fs.emptyDir(outputPath);

      // Restore reports directory
      if (reportsBackup && await fs.pathExists(reportsBackup)) {
        await fs.move(reportsBackup, reportsDir, { overwrite: true });
      }
    } else {
      await fs.ensureDir(outputPath);
    }

    // Create required subdirectories
    await fs.ensureDir(path.join(outputPath, 'styles'));
    await fs.ensureDir(path.join(outputPath, 'scripts'));
    await fs.ensureDir(path.join(outputPath, 'assets'));

    console.log('✅ Output directory prepared\n');
  }

  async discoverDocuments() {
    console.log('🔍 Discovering documents...');

    const docsPath = path.resolve(this.config.docsPath);
    console.log(`📂 Scanning: ${docsPath}`);

    // Initialize categories
    Object.keys(this.config.categories).forEach(key => {
      this.categories[key] = [];
    });

    // Process each category
    for (const [categoryKey, categoryConfig] of Object.entries(this.config.categories)) {
      const docs = await this.discoverCategoryDocuments(categoryKey, categoryConfig, docsPath);
      this.categories[categoryKey] = docs;
      console.log(`   ${categoryConfig.icon} ${categoryConfig.title}: ${docs.length} documents`);
    }

    // Flatten all documents
    this.documents = Object.values(this.categories).flat();

    console.log(`✅ Discovered ${this.documents.length} total documents\n`);
  }

  async discoverCategoryDocuments(categoryKey, categoryConfig, docsPath) {
    const documents = [];

    try {
      if (categoryConfig.folder) {
        // Scan folder for documents
        const folderPath = path.join(docsPath, categoryConfig.folder);

        if (await fs.pathExists(folderPath)) {
          const pattern = categoryConfig.pattern || '*.md';
          const files = await glob(pattern, {
            cwd: folderPath,
            absolute: false
          });

          for (const file of files) {
            const fullPath = path.join(folderPath, file);
            const relativePath = path.join(categoryConfig.folder, file);

            const outputInfo = this.getOutputPath(relativePath);
            documents.push({
              category: categoryKey,
              sourcePath: fullPath,
              relativePath: relativePath,
              outputPath: outputInfo.filePath,
              urlPath: outputInfo.urlPath,
              categoryConfig
            });
          }
        }
      } else if (categoryConfig.files) {
        // Process specific files
        for (const file of categoryConfig.files) {
          const fullPath = path.join(docsPath, file);

          if (await fs.pathExists(fullPath)) {
            const outputInfo = this.getOutputPath(file);
            documents.push({
              category: categoryKey,
              sourcePath: fullPath,
              relativePath: file,
              outputPath: outputInfo.filePath,
              urlPath: outputInfo.urlPath,
              categoryConfig
            });
          }
        }
      }
    } catch (error) {
      console.warn(`⚠️  Error discovering documents in category ${categoryKey}:`, error.message);
    }

    return documents;
  }

  getOutputPath(relativePath) {
    const ext = path.extname(relativePath);
    const nameWithoutExt = path.basename(relativePath, ext);
    const dir = path.dirname(relativePath);

    let outputDir = './dist';
    let urlPath = '';

    if (dir !== '.') {
      outputDir = path.join('./dist', dir);
      urlPath = dir + '/';
    }

    return {
      filePath: path.join(outputDir, `${nameWithoutExt}.html`),
      urlPath: `/${urlPath}${nameWithoutExt}.html`
    };
  }

  async processDocuments() {
    console.log('📝 Processing documents...');

    for (let i = 0; i < this.documents.length; i++) {
      const doc = this.documents[i];
      process.stdout.write(`   Processing (${i + 1}/${this.documents.length}): ${doc.relativePath}...`);

      try {
        // Check if this is an OpenAPI spec file
        if (doc.relativePath.endsWith('.openapi.yml') || doc.relativePath.endsWith('.openapi.yaml')) {
          const processed = await this.openapiProcessor.processFile(doc.sourcePath, doc.relativePath);

          // Add processed data to document
          doc.metadata = processed.metadata;
          doc.stats = processed.stats;
          doc.spec = processed.spec;
          doc.rawContent = processed.rawContent;
          doc.isOpenApi = true;

          // Keep track of API documents separately
          this.apiDocuments.push(doc);
        } else {
          const processed = await this.markdownProcessor.processFile(doc.sourcePath, doc.relativePath);

          // Add processed data to document
          doc.metadata = processed.metadata;
          doc.content = processed.content;
          doc.tableOfContents = processed.tableOfContents;
          doc.rawContent = processed.rawContent;
        }

        console.log(' ✅');
      } catch (error) {
        console.log(` ❌ Error: ${error.message}`);
      }
    }

    console.log('✅ Document processing completed\n');
  }

  async processAssets() {
    console.log('🖼️  Processing assets...');

    const docsPath = path.resolve(this.config.docsPath);
    const outputPath = path.resolve(this.config.outputPath);

    await this.assetProcessor.processAssets(docsPath, outputPath);

    // Update asset references in all documents
    for (const doc of this.documents) {
      if (doc.content) {
        doc.content = this.assetProcessor.updateAssetReferences(doc.content, doc.relativePath);
      }
    }

    await this.assetProcessor.saveAssetManifest(outputPath);

    console.log('✅ Asset processing completed\n');
  }

  async generatePages() {
    console.log('🏗️  Generating HTML pages...');

    // Generate index page
    await this.generateIndexPage();

    // Generate API index page if we have API documents
    if (this.apiDocuments.length > 0) {
      await this.generateApiIndexPage();
    }

    // Generate individual document pages
    for (let i = 0; i < this.documents.length; i++) {
      const doc = this.documents[i];
      process.stdout.write(`   Generating (${i + 1}/${this.documents.length}): ${doc.metadata.title}...`);

      try {
        if (doc.isOpenApi) {
          await this.generateOpenApiPage(doc);
        } else {
          await this.generateDocumentPage(doc);
        }
        console.log(' ✅');
      } catch (error) {
        console.log(` ❌ Error: ${error.message}`);
      }
    }

    console.log('✅ Page generation completed\n');
  }

  async generateIndexPage() {
    console.log('   Generating index page...');

    const indexHtml = await this.htmlGenerator.generateIndexPage(this.categories, this.documents);
    await fs.writeFile(path.join(this.config.outputPath, 'index.html'), indexHtml);

    console.log('   ✅ Index page generated');
  }

  async generateDocumentPage(doc) {
    const breadcrumbs = this.generateBreadcrumbs(doc);
    const navigation = this.htmlGenerator.buildNavigationData(this.categories, this.documents);

    const pageData = {
      metadata: doc.metadata,
      content: doc.content,
      tableOfContents: doc.tableOfContents,
      category: doc.category,
      navigation,
      breadcrumbs
    };

    const pageHtml = await this.htmlGenerator.generatePage(pageData);

    // Ensure output directory exists
    await fs.ensureDir(path.dirname(doc.outputPath));

    // Write the page
    await fs.writeFile(doc.outputPath, pageHtml);
  }

  async generateOpenApiPage(doc) {
    const pageHtml = await this.htmlGenerator.generateOpenApiPage(doc);

    // Ensure output directory exists
    await fs.ensureDir(path.dirname(doc.outputPath));

    // Write the page
    await fs.writeFile(doc.outputPath, pageHtml);

    // Also copy the raw YAML file for download
    const yamlOutputPath = path.join(path.dirname(doc.outputPath), path.basename(doc.sourcePath));
    await fs.copy(doc.sourcePath, yamlOutputPath);
  }

  async generateApiIndexPage() {
    console.log('   Generating API index page...');

    const indexHtml = await this.htmlGenerator.generateApiIndexPage(this.apiDocuments);
    const apiDir = path.join(this.config.outputPath, 'api');
    await fs.ensureDir(apiDir);
    await fs.writeFile(path.join(apiDir, 'index.html'), indexHtml);

    console.log('   ✅ API index page generated');
  }

  generateBreadcrumbs(doc) {
    const breadcrumbs = [
      { title: 'Home', url: '/' }
    ];

    if (doc.categoryConfig) {
      breadcrumbs.push({
        title: doc.categoryConfig.title,
        url: `/#${doc.category}`
      });
    }

    breadcrumbs.push({
      title: doc.metadata.title,
      url: doc.urlPath
    });

    return breadcrumbs;
  }

  async copyStaticFiles() {
    console.log('📄 Copying static files...');

    const outputPath = path.resolve(this.config.outputPath);

    // Copy CSS
    const cssSource = path.join(__dirname, '../src/templates/styles.css');
    const cssTarget = path.join(outputPath, 'styles/main.css');
    await fs.copy(cssSource, cssTarget);

    // Copy API-specific CSS
    const apiCssSource = path.join(__dirname, '../src/templates/api-docs.css');
    const apiCssTarget = path.join(outputPath, 'styles/api-docs.css');
    await fs.copy(apiCssSource, apiCssTarget);

    // Copy highlight.js CSS
    const { fileURLToPath } = await import('url');
    const hljsStylePath = fileURLToPath(import.meta.resolve('highlight.js/styles/github.css'));
    const hljsCSS = `/* Highlight.js GitHub Theme */
${await fs.readFile(hljsStylePath, 'utf8')}`;
    await fs.writeFile(path.join(outputPath, 'styles/highlight.css'), hljsCSS);

    // Create basic JavaScript file
    const jsContent = `
// BATbern Documentation Portal JavaScript

document.addEventListener('DOMContentLoaded', function() {
  // Initialize Mermaid diagrams
  if (typeof mermaid !== 'undefined') {
    mermaid.initialize({
      startOnLoad: true,
      theme: 'default',
      themeVariables: {
        primaryColor: '#1565C0',
        primaryTextColor: '#212121',
        primaryBorderColor: '#1976D2',
        lineColor: '#757575',
        sectionBkgColor: '#F5F5F5',
        altSectionBkgColor: '#FAFAFA',
        gridColor: '#E0E0E0',
        secondaryColor: '#FFB74D',
        tertiaryColor: '#FFFFFF'
      },
      securityLevel: 'loose',
      flowchart: {
        useMaxWidth: true,
        htmlLabels: true
      },
      sequence: {
        useMaxWidth: true,
        wrap: true
      },
      gantt: {
        useMaxWidth: true
      }
    });
  }
  // Mobile navigation toggle
  const navToggle = document.getElementById('navToggle');
  const sidebar = document.getElementById('sidebar');

  if (navToggle && sidebar) {
    navToggle.addEventListener('click', function() {
      sidebar.classList.toggle('open');
    });
  }

  // Sidebar scroll position preservation
  const sidebarScrollKey = 'batbern-sidebar-scroll';

  // Restore sidebar scroll position on page load
  const savedScrollPosition = localStorage.getItem(sidebarScrollKey);
  if (savedScrollPosition && sidebar) {
    sidebar.scrollTop = parseInt(savedScrollPosition, 10);
  }

  // Save sidebar scroll position before navigation
  if (sidebar) {
    // Save scroll position when clicking navigation links
    const navLinks = sidebar.querySelectorAll('.nav-link');
    navLinks.forEach(function(link) {
      link.addEventListener('click', function() {
        localStorage.setItem(sidebarScrollKey, sidebar.scrollTop.toString());
      });
    });

    // Also save on scroll (debounced to avoid excessive localStorage writes)
    let scrollTimeout;
    sidebar.addEventListener('scroll', function() {
      clearTimeout(scrollTimeout);
      scrollTimeout = setTimeout(function() {
        localStorage.setItem(sidebarScrollKey, sidebar.scrollTop.toString());
      }, 150);
    });
  }

  // Table of contents toggle
  const tocToggle = document.getElementById('tocToggle');
  const tocContent = document.getElementById('tocContent');

  if (tocToggle && tocContent) {
    tocToggle.addEventListener('click', function() {
      tocContent.style.display = tocContent.style.display === 'none' ? 'block' : 'none';
      const icon = tocToggle.querySelector('.toc-toggle-icon');
      if (icon) {
        icon.textContent = tocContent.style.display === 'none' ? '▶' : '▼';
      }
    });
  }

  // Back to top button
  const backToTop = document.getElementById('backToTop');

  if (backToTop) {
    window.addEventListener('scroll', function() {
      if (window.pageYOffset > 300) {
        backToTop.classList.add('visible');
      } else {
        backToTop.classList.remove('visible');
      }
    });

    backToTop.addEventListener('click', function() {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  // Share button
  const shareButton = document.getElementById('shareButton');

  if (shareButton) {
    shareButton.addEventListener('click', function() {
      if (navigator.share) {
        navigator.share({
          title: document.title,
          url: window.location.href
        });
      } else {
        // Fallback: copy to clipboard
        navigator.clipboard.writeText(window.location.href).then(function() {
          alert('Link copied to clipboard!');
        });
      }
    });
  }

  // Search functionality (basic)
  const searchInput = document.getElementById('searchInput');

  if (searchInput) {
    searchInput.addEventListener('input', function(e) {
      const query = e.target.value.toLowerCase();
      const navLinks = document.querySelectorAll('.nav-link');

      navLinks.forEach(function(link) {
        const text = link.textContent.toLowerCase();
        const item = link.closest('.nav-item');
        if (item) {
          if (query === '' || text.includes(query)) {
            item.style.display = '';
          } else {
            item.style.display = 'none';
          }
        }
      });
    });
  }

  // Smooth scrolling for anchor links
  document.querySelectorAll('a[href^="#"]').forEach(function(anchor) {
    anchor.addEventListener('click', function(e) {
      e.preventDefault();
      const href = this.getAttribute('href');

      // The ID in the HTML is already URL-encoded, so we use it as-is
      const targetId = href.substring(1); // Remove the # prefix

      try {
        const target = document.getElementById(targetId);
        if (target) {
          target.scrollIntoView({ behavior: 'smooth' });
        } else {
          console.warn('Target element not found:', targetId);
        }
      } catch (error) {
        console.warn('Error scrolling to element:', href, error);
      }
    });
  });

  // Initialize navigation state - all sections expanded except epic stories
  document.querySelectorAll('.nav-section').forEach(function(section) {
    section.classList.add('nav-section-expanded');
    const items = section.querySelector('.nav-section-items');
    const toggle = section.querySelector('.nav-section-toggle');
    if (items && toggle) {
      items.style.display = 'block';
      toggle.textContent = '▼';
    }
  });

  // Initialize epic stories as collapsed
  document.querySelectorAll('.epic-stories').forEach(function(stories) {
    stories.style.display = 'none';
  });

  document.querySelectorAll('.epic-toggle').forEach(function(toggle) {
    toggle.textContent = '▶';
  });

  console.log('🏔️ BATbern Documentation Portal loaded');
});

// Navigation section toggle functionality
function toggleNavSection(element) {
  const section = element.closest('.nav-section');
  const items = section.querySelector('.nav-section-items');
  const toggle = element.querySelector('.nav-section-toggle');

  if (items) {
    const isExpanded = section.classList.contains('nav-section-expanded');

    if (isExpanded) {
      section.classList.remove('nav-section-expanded');
      items.style.display = 'none';
      toggle.textContent = '▶';
    } else {
      section.classList.add('nav-section-expanded');
      items.style.display = 'block';
      toggle.textContent = '▼';
    }
  }
}

// Epic stories toggle functionality
function toggleEpicStories(element) {
  const epicContainer = element.closest('.nav-epic-container');
  const stories = epicContainer.querySelector('.epic-stories');
  const toggle = element.querySelector('.epic-toggle');

  if (stories) {
    const isVisible = stories.style.display !== 'none';

    if (isVisible) {
      stories.style.display = 'none';
      toggle.textContent = '▶';
    } else {
      stories.style.display = 'block';
      toggle.textContent = '▼';
    }
  }
}

// Category documents expand/collapse functionality
function toggleCategoryDocuments(categoryKey) {
  const card = document.querySelector('.category-card[data-category="' + categoryKey + '"]');
  if (!card) return;

  const hiddenDocs = card.querySelectorAll('.hidden-document');
  const button = card.querySelector('.expand-button');
  const expandText = button.querySelector('.expand-text');
  const collapseText = button.querySelector('.collapse-text');

  const isExpanded = hiddenDocs[0].style.display !== 'none';

  hiddenDocs.forEach(function(doc) {
    if (isExpanded) {
      doc.style.display = 'none';
    } else {
      doc.style.display = '';
    }
  });

  if (isExpanded) {
    expandText.style.display = '';
    collapseText.style.display = 'none';
  } else {
    expandText.style.display = 'none';
    collapseText.style.display = '';
  }
}
`;

    await fs.writeFile(path.join(outputPath, 'scripts/main.js'), jsContent);

    // Copy assets folder (favicon, logos, etc.)
    const assetsSource = path.join(__dirname, '../src/assets');
    const assetsTarget = path.join(outputPath, 'assets');

    if (await fs.pathExists(assetsSource)) {
      await fs.copy(assetsSource, assetsTarget);
      console.log('   ✅ Assets folder copied');
    }

    // Copy any additional static assets
    await this.assetProcessor.copyStaticAssets(outputPath);

    console.log('✅ Static files copied\n');
  }

  async generateManifest() {
    console.log('📋 Generating build manifest...');

    const manifest = {
      version: '1.0.0',
      buildTime: new Date().toISOString(),
      siteName: this.config.siteName,
      totalDocuments: this.documents.length,
      categories: Object.keys(this.categories).length,
      documents: this.documents.map(doc => ({
        title: doc.metadata.title,
        category: doc.category,
        relativePath: doc.relativePath,
        outputPath: doc.outputPath,
        lastModified: doc.metadata.lastModified,
        wordCount: doc.metadata.wordCount,
        storyCount: doc.metadata.storyCount || 0
      })),
      assets: this.assetProcessor.generateAssetManifest()
    };

    await fs.writeJson(path.join(this.config.outputPath, 'manifest.json'), manifest, { spaces: 2 });

    console.log('✅ Build manifest generated\n');
  }
}

// Run the builder if called directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const builder = new DocumentationBuilder();
  builder.build().catch(console.error);
}

export default DocumentationBuilder;