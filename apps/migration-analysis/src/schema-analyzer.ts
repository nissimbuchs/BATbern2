import * as fs from 'fs-extra';
import * as path from 'path';

// Types
export interface FieldOccurrence {
  count: number;
  percentage: number;
}

export interface OptionalField {
  name: string;
  occurrencePercentage: number;
}

export interface NestedSchema {
  fields: string[];
  types: Record<string, string>;
  samples: Record<string, unknown>;
}

export interface JsonSchemaAnalysis {
  fileName: string;
  totalRecords: number;
  fields: string[];
  types: Record<string, string>;
  samples: Record<string, unknown>;
  fieldOccurrences: Record<string, FieldOccurrence>;
  optionalFields: OptionalField[];
  nestedSchemas: Record<string, NestedSchema>;
}

export interface SchemaDocumentation {
  generatedAt: string;
  sessions: JsonSchemaAnalysis;
  topics: JsonSchemaAnalysis;
  pictures: JsonSchemaAnalysis;
}

// Core Analysis Functions
export async function extractFieldNames(jsonFilePath: string): Promise<string[]> {
  const data = await fs.readJSON(jsonFilePath);
  if (!Array.isArray(data)) return [];

  const fieldSet = new Set<string>();
  for (const record of data) {
    if (typeof record === 'object' && record !== null) {
      Object.keys(record).forEach((key) => fieldSet.add(key));
    }
  }

  return Array.from(fieldSet).sort();
}

export async function inferFieldTypes(jsonFilePath: string): Promise<Record<string, string>> {
  const data = await fs.readJSON(jsonFilePath);
  if (!Array.isArray(data) || data.length === 0) return {};

  const types: Record<string, string[]> = {};

  for (const record of data) {
    if (typeof record !== 'object' || record === null) continue;

    for (const [key, value] of Object.entries(record)) {
      if (!types[key]) types[key] = [];

      let type: string;
      if (value === null || value === undefined) {
        type = 'null';
      } else if (Array.isArray(value)) {
        type = 'array';
      } else {
        type = typeof value;
      }

      if (!types[key].includes(type)) {
        types[key].push(type);
      }
    }
  }

  // Consolidate types
  const result: Record<string, string> = {};
  for (const [key, typeArray] of Object.entries(types)) {
    if (typeArray.length === 1) {
      result[key] = typeArray[0];
    } else if (typeArray.includes('null') || typeArray.includes('undefined')) {
      const nonNullTypes = typeArray.filter((t) => t !== 'null' && t !== 'undefined');
      if (nonNullTypes.length === 1) {
        result[key] = `${nonNullTypes[0]}|undefined`;
      } else {
        result[key] = typeArray.join('|');
      }
    } else {
      result[key] = typeArray.join('|');
    }
  }

  return result;
}

export async function getSampleValues(
  jsonFilePath: string,
  maxSamples: number = 3
): Promise<Record<string, unknown>> {
  const data = await fs.readJSON(jsonFilePath);
  if (!Array.isArray(data) || data.length === 0) return {};

  const samples: Record<string, unknown[]> = {};
  const fields = await extractFieldNames(jsonFilePath);

  // Collect non-null samples for each field
  for (const field of fields) {
    samples[field] = [];

    for (const record of data) {
      if (samples[field].length >= maxSamples) break;

      if (record[field] !== undefined && record[field] !== null && record[field] !== '') {
        const value = record[field];

        // For arrays, take first element as sample
        if (Array.isArray(value) && value.length > 0) {
          samples[field].push(value[0]);
        } else if (!Array.isArray(value)) {
          // Truncate long strings
          if (typeof value === 'string' && value.length > 100) {
            samples[field].push(value.substring(0, 100) + '...');
          } else {
            samples[field].push(value);
          }
        }
      }
    }
  }

  // Return first sample for each field
  const result: Record<string, unknown> = {};
  for (const [key, values] of Object.entries(samples)) {
    if (values.length > 0) {
      result[key] = values[0];
    }
  }

  return result;
}

export async function analyzeJsonSchema(jsonFilePath: string): Promise<JsonSchemaAnalysis> {
  const data = await fs.readJSON(jsonFilePath);
  if (!Array.isArray(data)) {
    throw new Error(`Expected JSON array in ${jsonFilePath}`);
  }

  const totalRecords = data.length;
  const fields = await extractFieldNames(jsonFilePath);
  const types = await inferFieldTypes(jsonFilePath);
  const samples = await getSampleValues(jsonFilePath);

  // Calculate field occurrences
  const fieldCounts: Record<string, number> = {};
  for (const field of fields) {
    fieldCounts[field] = 0;
  }

  for (const record of data) {
    for (const field of fields) {
      if (record[field] !== undefined) {
        fieldCounts[field]++;
      }
    }
  }

  const fieldOccurrences: Record<string, FieldOccurrence> = {};
  const optionalFields: OptionalField[] = [];

  for (const field of fields) {
    const count = fieldCounts[field];
    const percentage = Math.round((count / totalRecords) * 100);
    fieldOccurrences[field] = { count, percentage };

    if (percentage < 100) {
      optionalFields.push({
        name: field,
        occurrencePercentage: percentage,
      });
    }
  }

  // Analyze nested schemas (arrays of objects)
  const nestedSchemas: Record<string, NestedSchema> = {};

  for (const field of fields) {
    if (types[field]?.includes('array')) {
      // Find first non-empty array to analyze its structure
      for (const record of data) {
        const arr = record[field];
        if (Array.isArray(arr) && arr.length > 0 && typeof arr[0] === 'object' && arr[0] !== null) {
          const nestedFields = new Set<string>();
          const nestedTypes: Record<string, string[]> = {};
          const nestedSamples: Record<string, unknown> = {};

          // Analyze all items in all arrays for this field
          for (const rec of data) {
            const items = rec[field];
            if (Array.isArray(items)) {
              for (const item of items) {
                if (typeof item === 'object' && item !== null) {
                  for (const [key, value] of Object.entries(item)) {
                    nestedFields.add(key);

                    if (!nestedTypes[key]) nestedTypes[key] = [];
                    const t = value === null ? 'null' : typeof value;
                    if (!nestedTypes[key].includes(t)) {
                      nestedTypes[key].push(t);
                    }

                    if (!nestedSamples[key] && value !== null && value !== undefined) {
                      if (typeof value === 'string' && value.length > 100) {
                        nestedSamples[key] = value.substring(0, 100) + '...';
                      } else {
                        nestedSamples[key] = value;
                      }
                    }
                  }
                }
              }
            }
          }

          const consolidatedTypes: Record<string, string> = {};
          for (const [key, typeArr] of Object.entries(nestedTypes)) {
            consolidatedTypes[key] = typeArr.length === 1 ? typeArr[0] : typeArr.join('|');
          }

          nestedSchemas[field] = {
            fields: Array.from(nestedFields).sort(),
            types: consolidatedTypes,
            samples: nestedSamples,
          };
          break;
        }
      }
    }
  }

  return {
    fileName: path.basename(jsonFilePath),
    totalRecords,
    fields,
    types,
    samples,
    fieldOccurrences,
    optionalFields: optionalFields.sort((a, b) => a.occurrencePercentage - b.occurrencePercentage),
    nestedSchemas,
  };
}

export async function generateSchemaDocumentation(legacyAppPath: string): Promise<SchemaDocumentation> {
  const apiPath = path.join(legacyAppPath, 'src/api');

  const sessions = await analyzeJsonSchema(path.join(apiPath, 'sessions.json'));
  const topics = await analyzeJsonSchema(path.join(apiPath, 'topics.json'));
  const pictures = await analyzeJsonSchema(path.join(apiPath, 'pictures.json'));

  return {
    generatedAt: new Date().toISOString(),
    sessions,
    topics,
    pictures,
  };
}

// Markdown Report Generator
export function generateMarkdownReport(doc: SchemaDocumentation): string {
  let md = '# JSON Schema Documentation\n\n';
  md += `Generated: ${doc.generatedAt}\n\n`;
  md += '## Overview\n\n';
  md += '| File | Total Records | Fields | Optional Fields |\n';
  md += '|------|---------------|--------|------------------|\n';
  md += `| sessions.json | ${doc.sessions.totalRecords} | ${doc.sessions.fields.length} | ${doc.sessions.optionalFields.length} |\n`;
  md += `| topics.json | ${doc.topics.totalRecords} | ${doc.topics.fields.length} | ${doc.topics.optionalFields.length} |\n`;
  md += `| pictures.json | ${doc.pictures.totalRecords} | ${doc.pictures.fields.length} | ${doc.pictures.optionalFields.length} |\n\n`;

  // Sessions Schema
  md += '---\n\n## sessions.json\n\n';
  md += `Total Records: ${doc.sessions.totalRecords}\n\n`;
  md += '### Fields\n\n';
  md += '| Field | Type | Occurrence | Sample Value |\n';
  md += '|-------|------|------------|---------------|\n';

  for (const field of doc.sessions.fields) {
    const type = doc.sessions.types[field] || 'unknown';
    const occ = doc.sessions.fieldOccurrences[field];
    const sample = doc.sessions.samples[field];
    let sampleStr = '';

    if (sample !== undefined) {
      if (typeof sample === 'object') {
        sampleStr = '(object)';
      } else if (typeof sample === 'string') {
        sampleStr = `\`${sample.substring(0, 50)}${sample.length > 50 ? '...' : ''}\``;
      } else {
        sampleStr = `\`${sample}\``;
      }
    }

    md += `| ${field} | ${type} | ${occ.percentage}% (${occ.count}/${doc.sessions.totalRecords}) | ${sampleStr} |\n`;
  }

  // Nested schema for referenten
  if (doc.sessions.nestedSchemas.referenten) {
    md += '\n### Nested Schema: referenten (Speaker)\n\n';
    md += '| Field | Type | Sample Value |\n';
    md += '|-------|------|---------------|\n';

    const nested = doc.sessions.nestedSchemas.referenten;
    for (const field of nested.fields) {
      const type = nested.types[field] || 'unknown';
      const sample = nested.samples[field];
      let sampleStr = '';

      if (sample !== undefined) {
        if (typeof sample === 'string') {
          sampleStr = `\`${sample.substring(0, 50)}${sample.length > 50 ? '...' : ''}\``;
        } else {
          sampleStr = `\`${sample}\``;
        }
      }

      md += `| ${field} | ${type} | ${sampleStr} |\n`;
    }
  }

  md += '\n### Optional Fields\n\n';
  if (doc.sessions.optionalFields.length > 0) {
    for (const field of doc.sessions.optionalFields) {
      md += `- **${field.name}**: ${field.occurrencePercentage}% of records\n`;
    }
  } else {
    md += 'All fields are required.\n';
  }

  // Topics Schema
  md += '\n---\n\n## topics.json\n\n';
  md += `Total Records: ${doc.topics.totalRecords}\n\n`;
  md += '### Fields\n\n';
  md += '| Field | Type | Occurrence | Sample Value |\n';
  md += '|-------|------|------------|---------------|\n';

  for (const field of doc.topics.fields) {
    const type = doc.topics.types[field] || 'unknown';
    const occ = doc.topics.fieldOccurrences[field];
    const sample = doc.topics.samples[field];
    let sampleStr = '';

    if (sample !== undefined) {
      if (typeof sample === 'string') {
        sampleStr = `\`${sample.substring(0, 50)}${sample.length > 50 ? '...' : ''}\``;
      } else {
        sampleStr = `\`${sample}\``;
      }
    }

    md += `| ${field} | ${type} | ${occ.percentage}% (${occ.count}/${doc.topics.totalRecords}) | ${sampleStr} |\n`;
  }

  // Pictures Schema
  md += '\n---\n\n## pictures.json\n\n';
  md += `Total Records: ${doc.pictures.totalRecords}\n\n`;
  md += '### Fields\n\n';
  md += '| Field | Type | Occurrence | Sample Value |\n';
  md += '|-------|------|------------|---------------|\n';

  for (const field of doc.pictures.fields) {
    const type = doc.pictures.types[field] || 'unknown';
    const occ = doc.pictures.fieldOccurrences[field];
    const sample = doc.pictures.samples[field];
    let sampleStr = '';

    if (sample !== undefined) {
      if (typeof sample === 'string') {
        sampleStr = `\`${sample.substring(0, 50)}${sample.length > 50 ? '...' : ''}\``;
      } else {
        sampleStr = `\`${sample}\``;
      }
    }

    md += `| ${field} | ${type} | ${occ.percentage}% (${occ.count}/${doc.pictures.totalRecords}) | ${sampleStr} |\n`;
  }

  return md;
}

// Main execution
async function main() {
  const legacyAppPath = path.resolve(__dirname, '../../BATspa-old');
  const outputDir = path.resolve(__dirname, '../../../docs/migration');

  console.log('Analyzing JSON Schemas...');
  console.log(`Source: ${legacyAppPath}`);

  const doc = await generateSchemaDocumentation(legacyAppPath);

  // Save JSON report
  const jsonReportPath = path.join(__dirname, '../reports/json-schema-analysis.json');
  await fs.ensureDir(path.dirname(jsonReportPath));
  await fs.writeJSON(jsonReportPath, doc, { spaces: 2 });
  console.log(`JSON report saved: ${jsonReportPath}`);

  // Generate and save Markdown report
  const markdownReport = generateMarkdownReport(doc);
  const mdReportPath = path.join(outputDir, 'json-schema-documentation.md');
  await fs.ensureDir(outputDir);
  await fs.writeFile(mdReportPath, markdownReport);
  console.log(`Markdown report saved: ${mdReportPath}`);

  console.log('\nSchema Analysis Summary:');
  console.log(`- sessions.json: ${doc.sessions.totalRecords} records, ${doc.sessions.fields.length} fields`);
  console.log(`- topics.json: ${doc.topics.totalRecords} records, ${doc.topics.fields.length} fields`);
  console.log(`- pictures.json: ${doc.pictures.totalRecords} records, ${doc.pictures.fields.length} fields`);
}

if (require.main === module) {
  main().catch(console.error);
}
