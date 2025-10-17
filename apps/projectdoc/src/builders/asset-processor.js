import fs from 'fs-extra';
import path from 'path';
import { glob } from 'glob';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class AssetProcessor {
  constructor(config) {
    this.config = config;
    this.processedAssets = new Map();
  }

  async processAssets(docsPath, outputPath) {
    console.log('Processing assets...');

    try {
      // Find all image and asset files in the docs directory
      const assetPatterns = [
        '**/*.png',
        '**/*.jpg',
        '**/*.jpeg',
        '**/*.gif',
        '**/*.svg',
        '**/*.pdf',
        '**/*.html',
        '**/*.css',
        '**/*.js'
      ];

      const allAssets = [];

      for (const pattern of assetPatterns) {
        const files = await glob(pattern, {
          cwd: docsPath,
          absolute: false
        });
        allAssets.push(...files);
      }

      console.log(`Found ${allAssets.length} assets to process`);

      // Process each asset
      for (const assetPath of allAssets) {
        await this.processAsset(assetPath, docsPath, outputPath);
      }

      console.log('Asset processing completed');
      return this.processedAssets;

    } catch (error) {
      console.error('Error processing assets:', error);
      throw error;
    }
  }

  async processAsset(relativePath, docsPath, outputPath) {
    const sourcePath = path.join(docsPath, relativePath);
    const targetPath = path.join(outputPath, 'assets', relativePath);

    try {
      // Ensure target directory exists
      await fs.ensureDir(path.dirname(targetPath));

      // Copy the asset file
      await fs.copy(sourcePath, targetPath);

      // Track the asset mapping for reference updates
      this.processedAssets.set(relativePath, {
        originalPath: relativePath,
        publicPath: `/assets/${relativePath}`,
        localPath: targetPath,
        size: (await fs.stat(sourcePath)).size
      });

      console.log(`Processed asset: ${relativePath}`);

    } catch (error) {
      console.warn(`Failed to process asset ${relativePath}:`, error.message);
    }
  }

  updateAssetReferences(htmlContent, documentPath) {
    let updatedContent = htmlContent;

    // Update image references
    this.processedAssets.forEach((asset, originalPath) => {
      const patterns = [
        // Markdown image syntax: ![alt](path)
        new RegExp(`!\\[([^\\]]*)\\]\\(\\s*${this.escapeRegExp(originalPath)}\\s*\\)`, 'g'),
        // HTML img tags
        new RegExp(`<img[^>]*src=['"]\\s*${this.escapeRegExp(originalPath)}\\s*['"][^>]*>`, 'g'),
        // CSS url() references
        new RegExp(`url\\(['"]?\\s*${this.escapeRegExp(originalPath)}\\s*['"]?\\)`, 'g'),
        // Direct href links
        new RegExp(`href=['"]\\s*${this.escapeRegExp(originalPath)}\\s*['"]`, 'g')
      ];

      patterns.forEach(pattern => {
        updatedContent = updatedContent.replace(pattern, (match) => {
          return match.replace(originalPath, asset.publicPath);
        });
      });
    });

    return updatedContent;
  }

  escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&');
  }

  async copyStaticAssets(outputPath) {
    const staticDir = path.join(__dirname, '../static');

    try {
      if (await fs.pathExists(staticDir)) {
        await fs.copy(staticDir, outputPath, {
          overwrite: true,
          filter: (src) => {
            // Skip hidden files and system files
            const basename = path.basename(src);
            return !basename.startsWith('.') && basename !== 'Thumbs.db';
          }
        });
        console.log('Static assets copied successfully');
      }
    } catch (error) {
      console.warn('No static assets directory found or error copying static assets:', error.message);
    }
  }

  generateAssetManifest() {
    const manifest = {
      version: '1.0.0',
      generatedAt: new Date().toISOString(),
      assets: {}
    };

    this.processedAssets.forEach((asset, key) => {
      manifest.assets[key] = {
        publicPath: asset.publicPath,
        size: asset.size
      };
    });

    return manifest;
  }

  async saveAssetManifest(outputPath) {
    const manifest = this.generateAssetManifest();
    const manifestPath = path.join(outputPath, 'assets', 'manifest.json');

    try {
      await fs.ensureDir(path.dirname(manifestPath));
      await fs.writeJson(manifestPath, manifest, { spaces: 2 });
      console.log('Asset manifest saved');
    } catch (error) {
      console.warn('Failed to save asset manifest:', error.message);
    }
  }

  // Helper method to get asset info
  getAssetInfo(originalPath) {
    return this.processedAssets.get(originalPath) || null;
  }

  // Helper method to get all processed assets
  getAllAssets() {
    return Array.from(this.processedAssets.values());
  }

  // Helper method to get assets by type
  getAssetsByType(extension) {
    return Array.from(this.processedAssets.values())
      .filter(asset => asset.originalPath.endsWith(extension));
  }
}

export default AssetProcessor;