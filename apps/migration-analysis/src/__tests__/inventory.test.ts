import * as path from 'path';
import {
  catalogJsonFiles,
  catalogAssetFiles,
  getFileSizeInfo,
  countRecordsInJsonFile,
  generateDataSourceCatalog,
} from '../inventory';

const LEGACY_APP_PATH = path.resolve(__dirname, '../../../BATspa-old');

describe('Data Source Inventory', () => {
  describe('AC1 - Data Source Catalog', () => {
    test('should_catalogAllJsonFiles_when_inventoryRun', async () => {
      const result = await catalogJsonFiles(LEGACY_APP_PATH);

      expect(result).toBeDefined();
      expect(result.length).toBeGreaterThan(0);

      // Should find at least these 3 core JSON files
      const fileNames = result.map((f) => f.name);
      expect(fileNames).toContain('sessions.json');
      expect(fileNames).toContain('topics.json');
      expect(fileNames).toContain('pictures.json');
    });

    test('should_includeFileSizes_when_catalogGenerated', async () => {
      const result = await catalogJsonFiles(LEGACY_APP_PATH);

      result.forEach((file) => {
        expect(file.size).toBeDefined();
        expect(typeof file.size).toBe('number');
        expect(file.size).toBeGreaterThan(0);
        expect(file.sizeFormatted).toBeDefined();
        expect(typeof file.sizeFormatted).toBe('string');
      });
    });

    test('should_countRecordsPerFile_when_inventoryRun', async () => {
      const result = await catalogJsonFiles(LEGACY_APP_PATH);

      result.forEach((file) => {
        expect(file.recordCount).toBeDefined();
        expect(typeof file.recordCount).toBe('number');
        expect(file.recordCount).toBeGreaterThanOrEqual(0);
      });

      // Verify specific counts based on known data
      const sessionsFile = result.find((f) => f.name === 'sessions.json');
      const topicsFile = result.find((f) => f.name === 'topics.json');
      const picturesFile = result.find((f) => f.name === 'pictures.json');

      expect(sessionsFile?.recordCount).toBeGreaterThan(300);
      expect(topicsFile?.recordCount).toBeGreaterThanOrEqual(60);
      expect(picturesFile?.recordCount).toBeGreaterThan(160);
    });
  });

  describe('AC3 - File Asset Inventory', () => {
    test('should_findAllPdfFiles_when_assetScanRun', async () => {
      const result = await catalogAssetFiles(LEGACY_APP_PATH);

      expect(result.pdfFiles).toBeDefined();
      expect(result.pdfFiles.length).toBeGreaterThan(0);

      // Each PDF file should have required properties
      result.pdfFiles.forEach((file) => {
        expect(file.name).toBeDefined();
        expect(file.path).toBeDefined();
        expect(file.size).toBeDefined();
        expect(file.name.toLowerCase()).toMatch(/\.(pdf|pptx?)$/);
      });
    });

    test('should_findAllImageFiles_when_assetScanRun', async () => {
      const result = await catalogAssetFiles(LEGACY_APP_PATH);

      expect(result.imageFiles).toBeDefined();
      expect(result.imageFiles.length).toBeGreaterThan(0);

      // Each image file should have required properties
      result.imageFiles.forEach((file) => {
        expect(file.name).toBeDefined();
        expect(file.path).toBeDefined();
        expect(file.size).toBeDefined();
        expect(file.name.toLowerCase()).toMatch(/\.(jpg|jpeg|png|gif|webp|svg)$/);
      });
    });

    test('should_calculateFileSizes_when_inventoryGenerated', async () => {
      const result = await catalogAssetFiles(LEGACY_APP_PATH);

      expect(result.totalPdfSize).toBeDefined();
      expect(result.totalPdfSize).toBeGreaterThan(0);
      expect(result.totalImageSize).toBeDefined();
      expect(result.totalImageSize).toBeGreaterThan(0);
      expect(result.totalSizeFormatted).toBeDefined();
      expect(typeof result.totalSizeFormatted).toBe('string');
    });

    test('should_categorizeImagesByType_when_inventoryGenerated', async () => {
      const result = await catalogAssetFiles(LEGACY_APP_PATH);

      expect(result.imagesByCategory).toBeDefined();
      expect(result.imagesByCategory.eventPhotos).toBeDefined();
      expect(result.imagesByCategory.partnerLogos).toBeDefined();
      expect(result.imagesByCategory.speakerPortraits).toBeDefined();
    });
  });

  describe('Utility Functions', () => {
    test('should_formatFileSize_when_bytesProvided', () => {
      expect(getFileSizeInfo(0)).toBe('0 B');
      expect(getFileSizeInfo(100)).toBe('100 B');
      expect(getFileSizeInfo(1024)).toBe('1.0 KB');
      expect(getFileSizeInfo(1536)).toBe('1.5 KB');
      expect(getFileSizeInfo(1048576)).toBe('1.0 MB');
      expect(getFileSizeInfo(1073741824)).toBe('1.0 GB');
    });

    test('should_countArrayRecords_when_jsonArrayProvided', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const count = await countRecordsInJsonFile(sessionsPath);

      expect(count).toBeGreaterThan(300);
    });
  });

  describe('Report Generation', () => {
    test('should_generateCompleteCatalog_when_allDataScanned', async () => {
      const catalog = await generateDataSourceCatalog(LEGACY_APP_PATH);

      expect(catalog).toBeDefined();
      expect(catalog.generatedAt).toBeDefined();
      expect(catalog.sourceDirectory).toBe(LEGACY_APP_PATH);
      expect(catalog.jsonFiles).toBeDefined();
      expect(catalog.assetFiles).toBeDefined();
      expect(catalog.summary).toBeDefined();
      expect(catalog.summary.totalJsonFiles).toBeGreaterThan(0);
      expect(catalog.summary.totalPdfFiles).toBeGreaterThan(0);
      expect(catalog.summary.totalImageFiles).toBeGreaterThan(0);
      expect(catalog.summary.totalStorageSize).toBeDefined();
    });
  });
});
