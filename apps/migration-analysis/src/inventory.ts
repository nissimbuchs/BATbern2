import * as fs from 'fs-extra';
import * as path from 'path';
import { glob } from 'glob';

// Types
export interface JsonFileInfo {
  name: string;
  path: string;
  size: number;
  sizeFormatted: string;
  recordCount: number;
  lastModified: string;
}

export interface AssetFileInfo {
  name: string;
  path: string;
  size: number;
  sizeFormatted: string;
  type: string;
}

export interface ImageCategories {
  eventPhotos: AssetFileInfo[];
  partnerLogos: AssetFileInfo[];
  speakerPortraits: AssetFileInfo[];
  other: AssetFileInfo[];
}

export interface AssetCatalog {
  pdfFiles: AssetFileInfo[];
  imageFiles: AssetFileInfo[];
  imagesByCategory: ImageCategories;
  totalPdfSize: number;
  totalImageSize: number;
  totalSizeFormatted: string;
}

export interface DataSourceCatalog {
  generatedAt: string;
  sourceDirectory: string;
  jsonFiles: JsonFileInfo[];
  assetFiles: AssetCatalog;
  summary: {
    totalJsonFiles: number;
    totalJsonRecords: number;
    totalPdfFiles: number;
    totalImageFiles: number;
    totalStorageSize: string;
  };
}

// Utility Functions
export function getFileSizeInfo(bytes: number): string {
  if (bytes === 0) return '0 B';

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  if (i === 0) return `${bytes} B`;

  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${units[i]}`;
}

export async function countRecordsInJsonFile(filePath: string): Promise<number> {
  try {
    const content = await fs.readJSON(filePath);
    if (Array.isArray(content)) {
      return content.length;
    }
    // If it's an object, count top-level keys
    if (typeof content === 'object' && content !== null) {
      return Object.keys(content).length;
    }
    return 0;
  } catch {
    return 0;
  }
}

// Core Functions
export async function catalogJsonFiles(legacyAppPath: string): Promise<JsonFileInfo[]> {
  const apiPath = path.join(legacyAppPath, 'src/api');
  const jsonFiles: JsonFileInfo[] = [];

  const files = await glob('*.json', { cwd: apiPath });

  for (const file of files) {
    const filePath = path.join(apiPath, file);
    const stats = await fs.stat(filePath);
    const recordCount = await countRecordsInJsonFile(filePath);

    jsonFiles.push({
      name: file,
      path: filePath,
      size: stats.size,
      sizeFormatted: getFileSizeInfo(stats.size),
      recordCount,
      lastModified: stats.mtime.toISOString(),
    });
  }

  return jsonFiles.sort((a, b) => a.name.localeCompare(b.name));
}

export async function catalogAssetFiles(legacyAppPath: string): Promise<AssetCatalog> {
  const pdfFiles: AssetFileInfo[] = [];
  const imageFiles: AssetFileInfo[] = [];
  const imagesByCategory: ImageCategories = {
    eventPhotos: [],
    partnerLogos: [],
    speakerPortraits: [],
    other: [],
  };

  // Scan for PDF/PPTX files in docs folder
  const docsPath = path.join(legacyAppPath, 'docs');
  if (await fs.pathExists(docsPath)) {
    const pdfPattern = '**/*.{pdf,pptx,ppt}';
    const pdfPaths = await glob(pdfPattern, {
      cwd: docsPath,
      nocase: true,
    });

    for (const pdfPath of pdfPaths) {
      const fullPath = path.join(docsPath, pdfPath);
      const stats = await fs.stat(fullPath);
      const ext = path.extname(pdfPath).toLowerCase();

      pdfFiles.push({
        name: path.basename(pdfPath),
        path: fullPath,
        size: stats.size,
        sizeFormatted: getFileSizeInfo(stats.size),
        type: ext.replace('.', '').toUpperCase(),
      });
    }
  }

  // Scan for PDF/PPTX and image files in archiv folder (main source of presentations)
  const archivPath = path.join(legacyAppPath, 'src/archiv');
  if (await fs.pathExists(archivPath)) {
    // PDFs in archiv
    const archivPdfPattern = '**/*.{pdf,pptx,ppt}';
    const archivPdfPaths = await glob(archivPdfPattern, {
      cwd: archivPath,
      nocase: true,
    });

    for (const pdfPath of archivPdfPaths) {
      const fullPath = path.join(archivPath, pdfPath);
      const stats = await fs.stat(fullPath);
      const ext = path.extname(pdfPath).toLowerCase();

      pdfFiles.push({
        name: path.basename(pdfPath),
        path: fullPath,
        size: stats.size,
        sizeFormatted: getFileSizeInfo(stats.size),
        type: ext.replace('.', '').toUpperCase(),
      });
    }

    // Images in archiv (speaker portraits and event thumbnails)
    const archivImagePattern = '**/*.{jpg,jpeg,png,gif,webp,svg}';
    const archivImagePaths = await glob(archivImagePattern, {
      cwd: archivPath,
      nocase: true,
    });

    for (const imagePath of archivImagePaths) {
      const fullPath = path.join(archivPath, imagePath);
      const stats = await fs.stat(fullPath);
      const ext = path.extname(imagePath).toLowerCase();

      const fileInfo: AssetFileInfo = {
        name: path.basename(imagePath),
        path: fullPath,
        size: stats.size,
        sizeFormatted: getFileSizeInfo(stats.size),
        type: ext.replace('.', '').toUpperCase(),
      };

      imageFiles.push(fileInfo);

      // Categorize archiv images - speaker portraits have names like "firstname.lastname.jpg"
      const lowerName = fileInfo.name.toLowerCase();
      if (lowerName.match(/^\d+_\d+_\d+\.jpg$/)) {
        // Thumbnail images like "00_120_80.jpg"
        imagesByCategory.other.push(fileInfo);
      } else if (lowerName.includes('.') && lowerName.split('.').length >= 3) {
        // Speaker portraits like "björn.müller.jpg"
        imagesByCategory.speakerPortraits.push(fileInfo);
      } else {
        imagesByCategory.other.push(fileInfo);
      }
    }
  }

  // Scan for image files in assets folder
  const assetsPath = path.join(legacyAppPath, 'src/assets');
  if (await fs.pathExists(assetsPath)) {
    const imagePattern = '**/*.{jpg,jpeg,png,gif,webp,svg}';
    const imagePaths = await glob(imagePattern, {
      cwd: assetsPath,
      nocase: true,
    });

    for (const imagePath of imagePaths) {
      const fullPath = path.join(assetsPath, imagePath);
      const stats = await fs.stat(fullPath);
      const ext = path.extname(imagePath).toLowerCase();

      const fileInfo: AssetFileInfo = {
        name: path.basename(imagePath),
        path: fullPath,
        size: stats.size,
        sizeFormatted: getFileSizeInfo(stats.size),
        type: ext.replace('.', '').toUpperCase(),
      };

      imageFiles.push(fileInfo);

      // Categorize images based on path
      const lowerPath = imagePath.toLowerCase();
      if (lowerPath.includes('partner')) {
        imagesByCategory.partnerLogos.push(fileInfo);
      } else if (lowerPath.includes('bilder') || lowerPath.includes('event')) {
        imagesByCategory.eventPhotos.push(fileInfo);
      } else if (
        lowerPath.includes('portrait') ||
        lowerPath.includes('speaker') ||
        lowerPath.includes('referent')
      ) {
        imagesByCategory.speakerPortraits.push(fileInfo);
      } else {
        imagesByCategory.other.push(fileInfo);
      }
    }
  }

  const totalPdfSize = pdfFiles.reduce((sum, f) => sum + f.size, 0);
  const totalImageSize = imageFiles.reduce((sum, f) => sum + f.size, 0);

  return {
    pdfFiles,
    imageFiles,
    imagesByCategory,
    totalPdfSize,
    totalImageSize,
    totalSizeFormatted: getFileSizeInfo(totalPdfSize + totalImageSize),
  };
}

export async function generateDataSourceCatalog(legacyAppPath: string): Promise<DataSourceCatalog> {
  const jsonFiles = await catalogJsonFiles(legacyAppPath);
  const assetFiles = await catalogAssetFiles(legacyAppPath);

  const totalJsonRecords = jsonFiles.reduce((sum, f) => sum + f.recordCount, 0);
  const totalJsonSize = jsonFiles.reduce((sum, f) => sum + f.size, 0);
  const totalStorageSize = totalJsonSize + assetFiles.totalPdfSize + assetFiles.totalImageSize;

  return {
    generatedAt: new Date().toISOString(),
    sourceDirectory: legacyAppPath,
    jsonFiles,
    assetFiles,
    summary: {
      totalJsonFiles: jsonFiles.length,
      totalJsonRecords,
      totalPdfFiles: assetFiles.pdfFiles.length,
      totalImageFiles: assetFiles.imageFiles.length,
      totalStorageSize: getFileSizeInfo(totalStorageSize),
    },
  };
}

// Report Generator
export async function generateMarkdownReport(catalog: DataSourceCatalog): Promise<string> {
  let md = '# Data Source Catalog\n\n';
  md += `Generated: ${catalog.generatedAt}\n\n`;
  md += `Source Directory: \`${catalog.sourceDirectory}\`\n\n`;

  md += '## Summary\n\n';
  md += `| Metric | Count | Size |\n`;
  md += `|--------|-------|------|\n`;
  md += `| JSON Data Files | ${catalog.summary.totalJsonFiles} | - |\n`;
  md += `| Total JSON Records | ${catalog.summary.totalJsonRecords} | - |\n`;
  md += `| PDF/PPTX Files | ${catalog.summary.totalPdfFiles} | ${catalog.assetFiles.totalSizeFormatted.split(' ')[0]} |\n`;
  md += `| Image Files | ${catalog.summary.totalImageFiles} | - |\n`;
  md += `| **Total Storage** | - | ${catalog.summary.totalStorageSize} |\n\n`;

  md += '## JSON Data Files\n\n';
  md += '| File | Records | Size | Last Modified |\n';
  md += '|------|---------|------|---------------|\n';
  for (const file of catalog.jsonFiles) {
    const modDate = new Date(file.lastModified).toLocaleDateString();
    md += `| ${file.name} | ${file.recordCount} | ${file.sizeFormatted} | ${modDate} |\n`;
  }
  md += '\n';

  md += '## Asset Files by Category\n\n';

  md += '### PDF/PPTX Documents\n\n';
  md += `Total: ${catalog.assetFiles.pdfFiles.length} files (${getFileSizeInfo(catalog.assetFiles.totalPdfSize)})\n\n`;
  if (catalog.assetFiles.pdfFiles.length > 0) {
    md += '<details>\n<summary>View all PDF files</summary>\n\n';
    md += '| File | Size | Type |\n';
    md += '|------|------|------|\n';
    for (const file of catalog.assetFiles.pdfFiles.slice(0, 50)) {
      md += `| ${file.name} | ${file.sizeFormatted} | ${file.type} |\n`;
    }
    if (catalog.assetFiles.pdfFiles.length > 50) {
      md += `\n... and ${catalog.assetFiles.pdfFiles.length - 50} more files\n`;
    }
    md += '\n</details>\n\n';
  }

  md += '### Image Files\n\n';
  md += `Total: ${catalog.assetFiles.imageFiles.length} files (${getFileSizeInfo(catalog.assetFiles.totalImageSize)})\n\n`;

  const categories = [
    { name: 'Event Photos', data: catalog.assetFiles.imagesByCategory.eventPhotos },
    { name: 'Partner Logos', data: catalog.assetFiles.imagesByCategory.partnerLogos },
    { name: 'Speaker Portraits', data: catalog.assetFiles.imagesByCategory.speakerPortraits },
    { name: 'Other Images', data: catalog.assetFiles.imagesByCategory.other },
  ];

  for (const category of categories) {
    if (category.data.length > 0) {
      const categorySize = category.data.reduce((sum, f) => sum + f.size, 0);
      md += `**${category.name}**: ${category.data.length} files (${getFileSizeInfo(categorySize)})\n\n`;
    }
  }

  return md;
}

// Main execution
async function main() {
  const legacyAppPath = path.resolve(__dirname, '../../BATspa-old');
  const outputDir = path.resolve(__dirname, '../../../docs/migration');

  console.log('Generating Data Source Catalog...');
  console.log(`Source: ${legacyAppPath}`);

  const catalog = await generateDataSourceCatalog(legacyAppPath);

  // Save JSON report
  const jsonReportPath = path.join(__dirname, '../reports/data-source-catalog.json');
  await fs.ensureDir(path.dirname(jsonReportPath));
  await fs.writeJSON(jsonReportPath, catalog, { spaces: 2 });
  console.log(`JSON report saved: ${jsonReportPath}`);

  // Generate and save Markdown report
  const markdownReport = await generateMarkdownReport(catalog);
  const mdReportPath = path.join(outputDir, 'data-source-catalog.md');
  await fs.ensureDir(outputDir);
  await fs.writeFile(mdReportPath, markdownReport);
  console.log(`Markdown report saved: ${mdReportPath}`);

  console.log('\nSummary:');
  console.log(`- JSON Files: ${catalog.summary.totalJsonFiles}`);
  console.log(`- Total Records: ${catalog.summary.totalJsonRecords}`);
  console.log(`- PDF/PPTX Files: ${catalog.summary.totalPdfFiles}`);
  console.log(`- Image Files: ${catalog.summary.totalImageFiles}`);
  console.log(`- Total Storage: ${catalog.summary.totalStorageSize}`);
}

if (require.main === module) {
  main().catch(console.error);
}
