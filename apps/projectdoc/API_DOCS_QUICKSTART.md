# API Documentation - Quick Start Guide

## 🎉 Implementation Complete!

Your OpenAPI documentation is now integrated into the BATbern project documentation portal!

## 🚀 Quick Start (3 Steps)

### Step 1: Install Dependencies

```bash
cd apps/projectdoc
npm install
```

This installs the new `redoc` package needed for interactive API documentation.

### Step 2: Build the Documentation

```bash
npm run build
```

This will:
- ✅ Process all 3 OpenAPI specifications (Companies, Events, Auth)
- ✅ Generate interactive API documentation pages
- ✅ Create the API index page at `/api/index.html`
- ✅ Copy all assets and styles

### Step 3: Preview Locally

```bash
npm run dev
```

Then open your browser to:
- **API Index**: http://localhost:3000/api/
- **Companies API**: http://localhost:3000/api/companies-api.html
- **Events API**: http://localhost:3000/api/events-api.html
- **Auth API**: http://localhost:3000/api/auth-endpoints.html

## 📁 What Was Created

### New Files
```
apps/projectdoc/
├── src/
│   ├── builders/
│   │   └── openapi-processor.js          [NEW] - Parses OpenAPI specs
│   └── templates/
│       ├── openapi-page.html             [NEW] - API doc page template
│       ├── api-index.html                [NEW] - API landing page
│       └── api-docs.css                  [NEW] - API styling
├── IMPLEMENTATION_SUMMARY.md             [NEW] - Full documentation
└── API_DOCS_QUICKSTART.md                [NEW] - This file
```

### Modified Files
```
apps/projectdoc/
├── package.json                          [MODIFIED] - Added redoc dependency
├── src/
│   ├── config/
│   │   └── site-config.js                [MODIFIED] - Added API category
│   └── builders/
│       └── html-generator.js             [MODIFIED] - Added API methods
└── scripts/
    └── build.js                          [MODIFIED] - Added API processing
```

## 🌐 URL Structure

After deployment to `project.batbern.ch`:

| URL | Content |
|-----|---------|
| `/api/` | API Documentation Landing Page |
| `/api/companies-api.html` | Interactive Companies API Docs |
| `/api/events-api.html` | Interactive Events API Docs |
| `/api/companies-api.openapi.yml` | Download Companies spec |
| `/api/events-api.openapi.yml` | Download Events spec |

## ✨ Features

### Interactive Documentation
- 🔍 **Search**: Search across all endpoints and schemas
- 📝 **Try It Out**: Test API calls directly in browser
- 💻 **Code Examples**: Automatic code generation (curl, JS, Python, etc.)
- 📊 **Schemas**: Explore request/response models
- 🔒 **Security**: Authentication requirements clearly shown

### BATbern Branded
- 🎨 Consistent header and navigation with main site
- 🏔️ BATbern colors, fonts, and logo
- 📱 Fully responsive (mobile, tablet, desktop)
- ⚡ Fast loading with CDN-delivered Redoc

### Developer-Friendly
- 📥 Download original OpenAPI specs
- 📈 Quick stats (endpoints, schemas, version)
- 🏷️ Organized by tags
- 📖 Clear descriptions and examples

## 🔄 Workflow

### Adding a New API

1. Create OpenAPI spec: `docs/api/my-new-api.openapi.yml`
2. Run `npm run build` in `apps/projectdoc`
3. Done! Your API appears automatically in navigation and index

### Updating an API

1. Edit the OpenAPI YAML file
2. Run `npm run build`
3. Changes reflected immediately

### Deploying to AWS

```bash
npm run deploy
```

Uploads everything to S3 → available at `project.batbern.ch`

## 📊 Current APIs

### 1. Companies API
- **File**: `docs/api/companies-api.openapi.yml`
- **Story**: 1.14
- **Endpoints**: 8 (create, read, update, delete, search, validate, verify)
- **Features**: Company management, Swiss UID validation, verification workflow

### 2. Events API
- **File**: `docs/api/events-api.openapi.yml`
- **Story**: 1.15a.1
- **Endpoints**: 25 (consolidated from 130!)
- **Features**: Event CRUD, sessions, registrations, analytics, bulk operations

## 🛠️ Troubleshooting

### Build fails with "Cannot find module 'redoc'"
```bash
cd apps/projectdoc
npm install
```

### APIs not showing up
Check that:
1. Files end with `.openapi.yml` or `.openapi.yaml`
2. Files are in `docs/api/` folder
3. Build ran successfully

### Styling looks wrong
Run `npm run clean && npm run build` to regenerate everything

## 📚 More Information

See `IMPLEMENTATION_SUMMARY.md` for complete technical documentation.

## 🎯 Next Steps

1. ✅ Run `npm install`
2. ✅ Run `npm run build`
3. ✅ Run `npm run dev` to preview
4. ✅ Test all three APIs
5. ✅ Deploy with `npm run deploy`

**You're all set!** 🚀

---

Questions? Check the implementation summary or the BATbern documentation site itself once deployed.
