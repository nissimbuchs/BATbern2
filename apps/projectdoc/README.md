# BATbern Project Documentation Portal

A comprehensive documentation portal that automatically transforms project documentation into a beautiful, publicly accessible website at `project.batbern.ch`.

## 🏔️ Overview

This mini-application automatically discovers, processes, and publishes all project documentation from the `/docs` folder to create a professional documentation website. It's designed specifically for the BATbern event management platform development project.

## 🚀 Features

- **Automatic Document Discovery**: Scans `/docs` folder for all markdown files
- **Smart Categorization**: Organizes documents by type (PRD, Architecture, Wireframes, Epics, etc.)
- **Professional Styling**: BATbern-branded design with responsive layout
- **Search Functionality**: Built-in search across all documentation
- **Navigation**: Auto-generated hierarchical navigation
- **AWS S3 Deployment**: One-command deployment to AWS S3 with CloudFront CDN
- **Asset Processing**: Handles images, PDFs, and other assets
- **Table of Contents**: Auto-generated TOC for long documents
- **Mobile Responsive**: Works perfectly on all devices

## 📋 Prerequisites

- Node.js 18+ and npm
- AWS CLI configured with appropriate permissions
- AWS account with S3, CloudFront, and Route 53 access

## 🛠️ Installation

```bash
cd apps/projectdoc
npm install
```

## 📖 Usage

### Build Documentation Website

Transform all markdown files in `/docs` into a static website:

```bash
npm run build
```

This command:
1. Scans the `/docs` folder for all markdown files
2. Processes each file and converts to HTML with syntax highlighting
3. Generates navigation structure from document hierarchy
4. Applies BATbern styling and layout templates
5. Processes and copies all assets (images, PDFs, etc.)
6. Creates a complete static website in the `dist/` folder

### Deploy to AWS S3

Deploy the generated website to AWS S3 and make it publicly accessible:

```bash
npm run deploy
```

This command:
1. Uploads all files from `dist/` to the configured S3 bucket
2. Configures S3 bucket for static website hosting
3. Sets up public read permissions
4. Optionally configures CloudFront CDN
5. Optionally sets up Route 53 DNS records

### Development Preview

Build and preview the documentation locally:

```bash
npm run dev
```

This starts a local HTTP server at `http://localhost:3000` to preview the generated documentation.

### Clean Build Output

Remove all generated files:

```bash
npm run clean
```

## ⚙️ Configuration

### Environment Variables

Set these environment variables for deployment:

```bash
# Required
export S3_BUCKET_NAME=project-batbern-ch
export AWS_REGION=eu-central-1

# Optional
export ROUTE53_HOSTED_ZONE_ID=Z1234567890ABC
export SSL_CERTIFICATE_ARN=arn:aws:acm:...
export SKIP_CLOUDFRONT=true
export SKIP_DNS=true
```

### Site Configuration

Customize the site in `src/config/site-config.js`:

```javascript
module.exports = {
  siteName: 'BATbern Platform Documentation',
  siteDescription: 'Comprehensive documentation for the BATbern platform',
  baseUrl: 'https://project.batbern.ch',

  // Document categories and their configurations
  categories: {
    'prd-enhanced': {
      title: 'Product Requirements (Enhanced)',
      description: 'Detailed product requirements and specifications',
      icon: '📋',
      order: 1,
      files: ['prd-enhanced.md']
    },
    // ... more categories
  }
};
```

## 📁 Generated Website Structure

The build process creates a structured website:

```
dist/
├── index.html                 # Homepage with project overview
├── styles/
│   ├── main.css              # BATbern-branded styles
│   └── highlight.css         # Syntax highlighting
├── scripts/
│   └── main.js              # Interactive functionality
├── assets/                   # Processed images and files
│   ├── images/
│   ├── pdfs/
│   └── manifest.json        # Asset manifest
├── architecture/             # Architecture documentation
│   ├── system-overview.html
│   ├── tech-stack.html
│   └── coding-standards.html
├── prd/                     # Epic documentation
│   ├── epic-1-foundation-stories.html
│   ├── epic-2-event-creation-publishing-stories.html
│   └── ...
├── wireframes/              # UI wireframes
└── manifest.json           # Build manifest
```

## 🎨 Document Categories

The portal automatically organizes documentation into these categories:

| Category | Description | Source |
|----------|-------------|---------|
| **PRD Enhanced** | Product requirements and specifications | `docs/prd-enhanced.md` |
| **Architecture** | System architecture and technical docs | `docs/architecture/` |
| **Wireframes** | UI design and wireframes | `docs/wireframes/` |
| **Epics** | Development epics with user stories | `docs/prd/epic-*.md` |
| **Project Docs** | General project documentation | Various files |
| **AWS & Infrastructure** | Infrastructure and setup guides | AWS-related files |

## 🎯 Document Processing Features

### Markdown Enhancement
- **Syntax Highlighting**: Code blocks with language-specific highlighting
- **Table of Contents**: Auto-generated TOC for easy navigation
- **Cross-References**: Automatic linking between documents
- **Asset Linking**: Proper handling of images and files

### Epic Story Extraction
- Automatically extracts user stories from epic files
- Counts and displays story information
- Links to acceptance criteria and implementation details

### Metadata Extraction
- Document titles, descriptions, and modification dates
- Word count and estimated reading time
- File size and category information

## 🚀 AWS Deployment Details

### S3 Configuration
- Static website hosting enabled
- Public read access configured
- CORS settings for web access
- Cache headers for optimal performance

### CloudFront CDN (Optional)
- Global content delivery
- SSL certificate integration
- Cache invalidation on updates
- Custom domain support

### Route 53 DNS (Optional)
- Custom domain configuration
- SSL certificate validation
- Health checks and monitoring

## 🔧 Development

### Project Structure

```
apps/projectdoc/
├── src/
│   ├── builders/             # Core processing modules
│   │   ├── markdown-processor.js
│   │   ├── html-generator.js
│   │   └── asset-processor.js
│   ├── templates/            # HTML templates and styles
│   │   ├── layout.html
│   │   └── styles.css
│   └── config/              # Configuration files
│       ├── site-config.js
│       └── aws-config.js
├── scripts/                 # Build and deployment scripts
│   ├── build.js
│   └── deploy.js
├── dist/                   # Generated website (created by build)
└── package.json
```

### Adding New Document Categories

1. Edit `src/config/site-config.js`
2. Add new category configuration:

```javascript
'new-category': {
  title: 'New Category',
  description: 'Description of the category',
  icon: '📚',
  order: 7,
  folder: 'path/to/folder',  // or files: ['file1.md', 'file2.md']
  pattern: '*.md'            // optional file pattern
}
```

### Customizing Styling

Edit `src/templates/styles.css` to customize:
- Colors and branding
- Typography and fonts
- Layout and spacing
- Component styling

### Extending Functionality

Key extension points:
- **Markdown Processing**: Add custom markdown extensions in `markdown-processor.js`
- **HTML Generation**: Customize page templates in `html-generator.js`
- **Asset Processing**: Add new asset types in `asset-processor.js`
- **Deployment**: Extend AWS integration in `deploy.js`

## 🤝 Integration with BATbern Project

This documentation portal is specifically designed for the BATbern event management platform:

- **Source Documents**: Reads from the main project's `/docs` folder
- **Epic Processing**: Understands the project's epic/story structure
- **Branding**: Uses BATbern colors, fonts, and visual identity
- **Architecture**: Aligns with the project's Domain-Driven Design approach
- **Deployment**: Integrates with the project's AWS infrastructure

## 📊 Build Output

After running `npm run build`, you'll see:

```
🏔️ BATbern Documentation Builder
================================

🔧 Initializing components...
✅ Components initialized

📁 Preparing output directory...
✅ Output directory prepared

🔍 Discovering documents...
📂 Scanning: /path/to/docs
   📋 Product Requirements (Enhanced): 1 documents
   🏗️ Architecture: 12 documents
   🎨 Wireframes & UI Design: 8 documents
   🚀 Development Epics: 7 documents
   📚 Project Documentation: 6 documents
   ☁️ AWS & Infrastructure: 4 documents
✅ Discovered 38 total documents

📝 Processing documents...
   Processing (1/38): prd-enhanced.md... ✅
   Processing (2/38): architecture/system-overview.md... ✅
   ...

🖼️ Processing assets...
Found 15 assets to process
Processed asset: wireframes/organizer-dashboard.png
✅ Asset processing completed

🏗️ Generating HTML pages...
   Generating index page...
   ✅ Index page generated
   Generating (1/38): Product Requirements (Enhanced)... ✅
   ...

📄 Copying static files...
✅ Static files copied

📋 Generating build manifest...
✅ Build manifest generated

✅ Build completed successfully!
📊 Generated 39 pages
📁 Output directory: /path/to/dist
```

## 🌐 Live Website

Once deployed, the documentation portal provides:

- **Professional Homepage**: Overview with project statistics
- **Categorized Navigation**: Easy browsing by document type
- **Search Functionality**: Find content across all documents
- **Responsive Design**: Perfect viewing on desktop, tablet, and mobile
- **Fast Loading**: Optimized assets and CDN delivery
- **SEO Friendly**: Proper meta tags and semantic HTML

The website automatically updates whenever you run the build and deploy commands, ensuring your documentation is always current and accessible to stakeholders, team members, and the broader community.

## 📝 License

This project is part of the BATbern platform and follows the same licensing terms.