import * as path from 'path';
import {
  analyzeJsonSchema,
  extractFieldNames,
  inferFieldTypes,
  getSampleValues,
  generateSchemaDocumentation,
} from '../schema-analyzer';

const LEGACY_APP_PATH = path.resolve(__dirname, '../../../BATspa-old');

describe('JSON Schema Analysis', () => {
  describe('AC2 - JSON Schema Documentation', () => {
    test('should_extractAllFieldNames_when_schemaAnalyzed', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const fields = await extractFieldNames(sessionsPath);

      expect(fields).toBeDefined();
      expect(fields.length).toBeGreaterThan(0);

      // Sessions should have these fields
      expect(fields).toContain('bat');
      expect(fields).toContain('pdf');
      expect(fields).toContain('title');
      expect(fields).toContain('abstract');
      expect(fields).toContain('referenten');
      expect(fields).toContain('authoren');
    });

    test('should_identifyFieldTypes_when_schemaDocumented', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const schema = await inferFieldTypes(sessionsPath);

      expect(schema).toBeDefined();
      expect(schema.bat).toBe('number');
      expect(schema.pdf).toBe('string');
      expect(schema.title).toBe('string');
      expect(schema.abstract).toMatch(/string|undefined/);
      expect(schema.referenten).toMatch(/array|undefined/);
      expect(schema.authoren).toMatch(/string|undefined/);
    });

    test('should_provideSampleValues_when_schemaGenerated', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const samples = await getSampleValues(sessionsPath);

      expect(samples).toBeDefined();
      expect(samples.bat).toBeDefined();
      expect(typeof samples.bat).toBe('number');
      expect(samples.pdf).toBeDefined();
      expect(typeof samples.pdf).toBe('string');
      expect(samples.title).toBeDefined();
    });

    test('should_analyzeNestedObjects_when_speakersPresent', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const schema = await analyzeJsonSchema(sessionsPath);

      expect(schema.nestedSchemas).toBeDefined();
      expect(schema.nestedSchemas.referenten).toBeDefined();

      const speakerSchema = schema.nestedSchemas.referenten;
      expect(speakerSchema.fields).toContain('name');
      expect(speakerSchema.fields).toContain('bio');
      expect(speakerSchema.fields).toContain('company');
      expect(speakerSchema.fields).toContain('portrait');
    });

    test('should_countFieldOccurrences_when_schemaAnalyzed', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const schema = await analyzeJsonSchema(sessionsPath);

      expect(schema.fieldOccurrences).toBeDefined();
      expect(schema.fieldOccurrences.bat).toBeDefined();
      expect(schema.fieldOccurrences.bat.count).toBeGreaterThan(0);
      expect(schema.fieldOccurrences.bat.percentage).toBeGreaterThan(0);

      // bat should be in all records
      expect(schema.fieldOccurrences.bat.percentage).toBe(100);
    });

    test('should_identifyOptionalFields_when_notAllRecordsHaveField', async () => {
      const sessionsPath = path.join(LEGACY_APP_PATH, 'src/api/sessions.json');
      const schema = await analyzeJsonSchema(sessionsPath);

      // abstract and referenten are optional (not in all records)
      expect(schema.optionalFields).toBeDefined();
      expect(schema.optionalFields.length).toBeGreaterThan(0);

      // Check that some fields are optional
      const optionalFieldNames = schema.optionalFields.map((f) => f.name);
      expect(optionalFieldNames).toContain('abstract');
    });
  });

  describe('Topics Schema', () => {
    test('should_analyzeTopicsSchema_when_fileProvided', async () => {
      const topicsPath = path.join(LEGACY_APP_PATH, 'src/api/topics.json');
      const schema = await analyzeJsonSchema(topicsPath);

      expect(schema.fields).toContain('bat');
      expect(schema.fields).toContain('topic');
      expect(schema.fields).toContain('datum');
      expect(schema.fields).toContain('eventType');

      expect(schema.types.bat).toBe('number');
      expect(schema.types.topic).toBe('string');
      expect(schema.types.datum).toBe('string');
      expect(schema.types.eventType).toBe('string');
    });
  });

  describe('Pictures Schema', () => {
    test('should_analyzePicturesSchema_when_fileProvided', async () => {
      const picturesPath = path.join(LEGACY_APP_PATH, 'src/api/pictures.json');
      const schema = await analyzeJsonSchema(picturesPath);

      expect(schema.fields).toContain('bat');
      expect(schema.fields).toContain('image'); // Note: field is 'image', not 'picture'
    });
  });

  describe('Report Generation', () => {
    test('should_generateCompleteDocumentation_when_allSchemasAnalyzed', async () => {
      const report = await generateSchemaDocumentation(LEGACY_APP_PATH);

      expect(report).toBeDefined();
      expect(report.sessions).toBeDefined();
      expect(report.topics).toBeDefined();
      expect(report.pictures).toBeDefined();
      expect(report.generatedAt).toBeDefined();
    });
  });
});
