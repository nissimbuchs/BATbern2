const MarkdownIt = require('markdown-it');
const hljs = require('highlight.js');
const fs = require('fs-extra');
const path = require('path');
const cheerio = require('cheerio');

// Markdown-it plugins
const markdownItMermaid = require('markdown-it-mermaid').default;
const markdownItAnchor = require('markdown-it-anchor');
const markdownItToc = require('markdown-it-table-of-contents');
const markdownItContainer = require('markdown-it-container');
const markdownItFootnote = require('markdown-it-footnote');
const markdownItTaskLists = require('markdown-it-task-lists');

class MarkdownProcessor {
  constructor(config) {
    this.config = config;
    this.setupMarkdownIt();
  }

  setupMarkdownIt() {
    // Initialize markdown-it with options
    this.md = new MarkdownIt({
      html: true,
      linkify: true,
      typographer: true,
      breaks: false,
      highlight: function(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
          try {
            return hljs.highlight(code, { language: lang }).value;
          } catch (err) {
            console.warn(`Failed to highlight code block with language '${lang}':`, err.message);
          }
        }
        return hljs.highlightAuto(code).value;
      }
    });

    // Add plugins
    this.md
      .use(markdownItMermaid)
      .use(markdownItAnchor, {
        level: [1, 2, 3, 4, 5, 6],
        permalink: markdownItAnchor.permalink.ariaHidden({
          placement: 'before',
          symbol: '#',
          class: 'heading-link'
        })
      })
      .use(markdownItToc, {
        includeLevel: [1, 2, 3, 4],
        containerClass: 'table-of-contents',
        listType: 'ul'
      })
      .use(markdownItTaskLists, {
        enabled: true,
        label: true,
        labelAfter: true
      })
      .use(markdownItFootnote)
      .use(markdownItContainer, 'warning', {
        validate: function(params) {
          return params.trim().match(/^warning\s+(.*)$/);
        },
        render: function(tokens, idx) {
          const m = tokens[idx].info.trim().match(/^warning\s+(.*)$/);
          if (tokens[idx].nesting === 1) {
            return '<div class="warning-container"><div class="warning-title">⚠️ ' +
                   (m ? m[1] : 'Warning') + '</div><div class="warning-content">\n';
          } else {
            return '</div></div>\n';
          }
        }
      })
      .use(markdownItContainer, 'note', {
        validate: function(params) {
          return params.trim().match(/^note\s+(.*)$/);
        },
        render: function(tokens, idx) {
          const m = tokens[idx].info.trim().match(/^note\s+(.*)$/);
          if (tokens[idx].nesting === 1) {
            return '<div class="note-container"><div class="note-title">📝 ' +
                   (m ? m[1] : 'Note') + '</div><div class="note-content">\n';
          } else {
            return '</div></div>\n';
          }
        }
      });

    // Custom table renderer for better styling
    const defaultTableRender = this.md.renderer.rules.table_open || function(tokens, idx, options, env, renderer) {
      return renderer.renderToken(tokens, idx, options);
    };

    this.md.renderer.rules.table_open = function(tokens, idx, options, env, renderer) {
      return '<div class="table-wrapper"><table class="doc-table">';
    };

    const defaultTableCloseRender = this.md.renderer.rules.table_close || function(tokens, idx, options, env, renderer) {
      return renderer.renderToken(tokens, idx, options);
    };

    this.md.renderer.rules.table_close = function(tokens, idx, options, env, renderer) {
      return '</table></div>';
    };

    // Custom code block renderer
    const defaultCodeBlockRender = this.md.renderer.rules.code_block || function(tokens, idx, options, env, renderer) {
      return renderer.renderToken(tokens, idx, options);
    };

    this.md.renderer.rules.code_block = function(tokens, idx, options, env, renderer) {
      const token = tokens[idx];
      const lang = token.info ? token.info.trim() : '';
      const highlighted = options.highlight ? options.highlight(token.content, lang) : token.content;

      return `<div class="code-block">
        ${lang ? `<div class="code-header">${lang}</div>` : ''}
        <pre><code class="hljs language-${lang}">${highlighted}</code></pre>
      </div>`;
    };

    // Custom fence renderer for code blocks with language
    const defaultFenceRender = this.md.renderer.rules.fence || function(tokens, idx, options, env, renderer) {
      return renderer.renderToken(tokens, idx, options);
    };

    this.md.renderer.rules.fence = function(tokens, idx, options, env, renderer) {
      const token = tokens[idx];
      const lang = token.info ? token.info.trim() : '';

      // Handle mermaid diagrams specially
      if (lang === 'mermaid') {
        return `<div class="mermaid-container">
          <div class="mermaid">${token.content}</div>
        </div>`;
      }

      const highlighted = options.highlight ? options.highlight(token.content, lang) : token.content;

      return `<div class="code-block">
        ${lang ? `<div class="code-header">${lang}</div>` : ''}
        <pre><code class="hljs language-${lang}">${highlighted}</code></pre>
      </div>`;
    };

    // Custom link renderer to transform .md links to .html
    const defaultLinkOpenRender = this.md.renderer.rules.link_open || function(tokens, idx, options, env, renderer) {
      return renderer.renderToken(tokens, idx, options);
    };

    this.md.renderer.rules.link_open = function(tokens, idx, options, env, renderer) {
      const token = tokens[idx];
      const hrefIndex = token.attrIndex('href');

      if (hrefIndex >= 0) {
        const href = token.attrs[hrefIndex][1];

        // Transform .md links to .html (excluding external links and anchors)
        if (href && !href.startsWith('http') && !href.startsWith('#') && !href.startsWith('mailto:')) {
          if (href.endsWith('.md')) {
            token.attrs[hrefIndex][1] = href.replace(/\.md$/, '.html');
          } else if (href.includes('.md#')) {
            // Handle links with anchors like ./file.md#section
            token.attrs[hrefIndex][1] = href.replace(/\.md#/, '.html#');
          }
        }
      }

      return defaultLinkOpenRender(tokens, idx, options, env, renderer);
    };
  }

  async processFile(filePath, relativePath) {
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      const stats = await fs.stat(filePath);

      // Extract metadata from the file
      const metadata = this.extractMetadata(content, relativePath, stats);

      // Check if this is an epic file and add story links
      const isEpic = relativePath.match(/epic-\d+.*\.md$/);
      let processedContent = content;

      if (isEpic) {
        processedContent = await this.addStoryLinksToEpic(content, relativePath);
      }

      // Process the markdown content
      const htmlContent = this.md.render(processedContent);

      // Generate table of contents
      const toc = this.generateTableOfContents(htmlContent);

      return {
        metadata,
        content: htmlContent,
        tableOfContents: toc,
        rawContent: content
      };
    } catch (error) {
      console.error(`Error processing file ${filePath}:`, error);
      throw error;
    }
  }

  extractMetadata(content, relativePath, stats) {
    const lines = content.split('\n');
    const firstLine = lines[0] || '';

    // Extract title from first heading or filename
    let title = path.basename(relativePath, '.md')
      .replace(/[-_]/g, ' ')
      .replace(/\b\w/g, l => l.toUpperCase());

    const headingMatch = firstLine.match(/^#\s+(.+)$/);
    if (headingMatch) {
      title = headingMatch[1];
    }

    // Check if this is a wireframe file and extract story number from filename
    const isWireframe = relativePath.includes('wireframes/');
    if (isWireframe) {
      const wireframeStoryMatch = path.basename(relativePath).match(/^story-(\d+)\.(\d+(?:\.\d+)?)/);
      if (wireframeStoryMatch) {
        const epicNum = wireframeStoryMatch[1];
        const storyNum = wireframeStoryMatch[2];
        const storyRef = `Story ${epicNum}.${storyNum}`;

        // Check if title already contains a story reference
        const hasStoryRef = title.match(/^Story\s+\d+\.?\d*/i) || title.match(/Story\s*:/i);

        if (!hasStoryRef) {
          // Prepend story reference to title
          title = `${storyRef}: ${title}`;
        } else if (title.match(/^Story\s*:/i) && !title.match(/^Story\s+\d+/)) {
          // Replace generic "Story:" with specific story number
          title = title.replace(/^Story\s*:/i, `${storyRef}:`);
        }
      }
    }

    // Extract description from content
    let description = '';
    const descriptionMatch = content.match(/^##?\s+[^#\n]*\n\n([^#\n]+)/);
    if (descriptionMatch) {
      description = descriptionMatch[1].replace(/\*\*|__/g, '').substring(0, 200);
    }

    // Extract epic/story information if applicable
    const epicMatch = relativePath.match(/epic-(\d+)[-_](.+)\.md$/);
    let epicInfo = null;
    if (epicMatch) {
      epicInfo = {
        number: parseInt(epicMatch[1]),
        name: epicMatch[2].replace(/[-_]/g, ' ')
      };
    }

    // Count stories in epic files
    let storyCount = 0;
    if (content.includes('Story')) {
      // Check for both ## Story and ### Story formats
      storyCount = (content.match(/^## Story \d+\.\d+:/gm) || []).length;
    }

    return {
      title,
      description,
      relativePath,
      fileName: path.basename(relativePath),
      lastModified: stats.mtime,
      size: stats.size,
      epicInfo,
      storyCount,
      wordCount: content.split(/\s+/).length,
      readingTime: Math.ceil(content.split(/\s+/).length / 200) // ~200 words per minute
    };
  }

  generateTableOfContents(htmlContent) {
    const $ = cheerio.load(htmlContent);
    const toc = [];
    const maxDepth = this.config.navigation.maxTocDepth || 3;

    $('h1, h2, h3, h4, h5, h6').each((index, element) => {
      const $el = $(element);
      const level = parseInt(element.tagName.substring(1));

      if (level <= maxDepth) {
        const text = $el.text().replace('#', '').trim();
        const id = $el.attr('id') || text.toLowerCase()
          .replace(/[^\w\s-]/g, '')
          .replace(/\s+/g, '-');

        toc.push({
          level,
          text,
          id,
          children: []
        });
      }
    });

    return this.buildTocHierarchy(toc);
  }

  buildTocHierarchy(flatToc) {
    const result = [];
    const stack = [];

    for (const item of flatToc) {
      // Find the correct parent level
      while (stack.length > 0 && stack[stack.length - 1].level >= item.level) {
        stack.pop();
      }

      if (stack.length === 0) {
        result.push(item);
      } else {
        const parent = stack[stack.length - 1];
        if (!parent.children) parent.children = [];
        parent.children.push(item);
      }

      stack.push(item);
    }

    return result;
  }

  async addStoryLinksToEpic(content, relativePath) {
    // Extract epic number from the file path
    const epicMatch = relativePath.match(/epic-(\d+)/);
    if (!epicMatch) return content;

    const epicNumber = epicMatch[1];
    const docsPath = path.resolve(this.config.docsPath);
    const storiesPath = path.join(docsPath, 'stories');

    // Replace story headers with links if corresponding story files exist
    const storyRegex = /^## (Story \d+\.\d+): (.+)$/gm;

    const processedContent = content.replace(storyRegex, (match, storyPrefix, title) => {
      const storyMatch = storyPrefix.match(/Story (\d+)\.(\d+)/);
      if (!storyMatch) return match;

      const [, epicNum, storyNum] = storyMatch;
      if (epicNum !== epicNumber) return match;

      // Create the expected story filename
      const storySlug = title.toLowerCase()
        .replace(/[^a-z0-9\s-]/g, '')
        .replace(/\s+/g, '-')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '');

      const storyFilename = `${epicNum}.${storyNum}.${storySlug}.md`;
      const storyFilePath = path.join(storiesPath, storyFilename);

      // Check if story file exists
      try {
        if (fs.existsSync(storyFilePath)) {
          // Create a link to the story
          const storyUrl = `/stories/${storyFilename.replace('.md', '.html')}`;
          // Keep the header structure but make just the text a link
          // This preserves the auto-generated ID from marked
          return `## ${storyPrefix}: [${title}](${storyUrl})`;
        }
      } catch (error) {
        // If file doesn't exist or can't be accessed, return original
      }

      return match;
    });

    return processedContent;
  }

  async extractStoriesFromEpic(content) {
    const stories = [];
    const storyRegex = /### Story (\d+\.\d+): (.+?)\n\n([\s\S]*?)(?=\n### |$)/g;
    let match;

    while ((match = storyRegex.exec(content)) !== null) {
      const [, storyId, title, storyContent] = match;

      // Extract acceptance criteria
      const criteriaMatch = storyContent.match(/\*\*Acceptance Criteria:\*\*([\s\S]*?)(?=\*\*|$)/);
      const criteria = criteriaMatch ? criteriaMatch[1].trim() : '';

      stories.push({
        id: storyId,
        title: title.trim(),
        content: storyContent.trim(),
        acceptanceCriteria: criteria
      });
    }

    return stories;
  }
}

module.exports = MarkdownProcessor;