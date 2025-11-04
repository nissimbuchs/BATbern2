# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Next.js 16 project using:
- **React 19.2.0** with the App Router architecture
- **TypeScript** with strict mode enabled
- **Tailwind CSS 4** via PostCSS plugin (@tailwindcss/postcss)
- **ESLint** with Next.js config (core-web-vitals and TypeScript rules)

## Common Commands

Development:
```bash
npm run dev          # Start dev server at http://localhost:3000
npm run build        # Production build
npm start            # Start production server
npm run lint         # Run ESLint
```

## Architecture

### App Router Structure
- Uses Next.js App Router (not Pages Router)
- Routes defined in `app/` directory
- `app/layout.tsx` - Root layout with Geist fonts (sans and mono)
- `app/page.tsx` - Home page (Server Component by default)
- `app/globals.css` - Global styles

### TypeScript Configuration
- Path alias: `@/*` maps to project root
- Target: ES2017
- Module resolution: bundler
- JSX: react-jsx (automatic runtime)
- Strict mode enabled

### Styling
- Tailwind CSS 4 configured via PostCSS plugin
- Dark mode support built into default template
- Geist font family loaded via next/font/google

## Development Patterns

### Creating New Pages
- Add `page.tsx` in `app/[route-name]/` directory
- Server Components by default (no "use client" needed)
- Use Client Components only when needed (interactivity, hooks, browser APIs)

### Import Paths
Use the `@/` alias for cleaner imports:
```typescript
import { Component } from "@/components/Component"
import { utils } from "@/lib/utils"
```

### Image Optimization
Use Next.js Image component for all images:
```typescript
import Image from "next/image"
// Images in public/ are served from root path
<Image src="/image.svg" alt="..." width={x} height={y} />
```

## Notes

- This project uses React 19 with the new JSX runtime
- Tailwind CSS 4 is configured differently than v3 (uses PostCSS plugin, not tailwind.config)
- ESLint config uses the new flat config format (eslint.config.mjs)
- Server Components are the default; add "use client" only when necessary
