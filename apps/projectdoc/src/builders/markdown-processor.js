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
  }

  async processFile(filePath, relativePath) {
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      const stats = await fs.stat(filePath);

      // Extract metadata from the file
      const metadata = this.extractMetadata(content, relativePath, stats);

      // Process the markdown content
      const htmlContent = this.md.render(content);

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