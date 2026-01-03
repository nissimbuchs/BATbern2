# Batch Import Pattern (Frontend)

**Category**: Frontend
**Used in Stories**: 3.1 (Companies, Speakers, Events, Sessions), 3.2 (Participants)
**Last Updated**: 2025-12-25

## Overview

This template provides a reusable pattern for implementing batch import functionality in the BATbern frontend. All batch imports follow a consistent 4-file architecture with standardized UX patterns.

**When to use**: When you need to import multiple entities (10+) from a file (JSON/CSV) into the system.

## Prerequisites

- React 19+ with TypeScript
- Material-UI 7+ for components
- React Query 5+ for cache management
- react-dropzone for file upload
- Papa Parse (for CSV imports only)

---

## Architecture: 4-File Pattern

All batch imports follow this structure:

```
web-frontend/src/
├── components/{domain}/{Entity}/
│   └── {Entity}BatchImportModal.tsx          # Modal UI component
├── hooks/use{Entity}BatchImport/
│   └── use{Entity}BatchImport.ts             # Business logic hook
├── types/
│   └── {entity}Import.types.ts               # TypeScript interfaces
└── utils/
    └── {entity}Import.ts                     # Data transformation utilities
```

**Example**: For company batch import:
- `components/shared/Company/CompanyBatchImportModal.tsx`
- `hooks/useCompanyBatchImport/useCompanyBatchImport.ts`
- `types/companyImport.types.ts`
- `utils/companyImport.ts`

---

## Implementation Steps

### Step 1: Define Types (`types/{entity}Import.types.ts`)

```typescript
import { components } from '@/types/generated/api.types';

// Source data format (from file)
export interface Source{Entity} {
  // Fields from JSON/CSV file
  id: string;
  name: string;
  // ... other source fields
}

// Import candidate (transformed for UI)
export interface {Entity}ImportCandidate {
  // Source data
  name: string;
  email?: string;

  // Import metadata
  importStatus: ImportStatus;
  errorMessage?: string;

  // Existing entity reference (for updates)
  existing{Entity}Id?: string;
}

// Import status lifecycle
export type ImportStatus =
  | 'pending'      // Not yet processed
  | 'importing'    // Currently processing
  | 'success'      // Successfully created
  | 'updated'      // Successfully updated (if applicable)
  | 'error'        // Failed with error
  | 'skipped';     // Skipped (duplicate, no changes, etc.)

// Import result summary
export interface {Entity}BatchImportResult {
  total: number;
  success: number;
  updated?: number;    // Optional: only if update logic exists
  failed: number;
  skipped: number;
}

// Modal props
export interface {Entity}BatchImportModalProps {
  open: boolean;
  onClose: () => void;
}
```

---

### Step 2: Create Utilities (`utils/{entity}Import.ts`)

```typescript
import { Source{Entity}, {Entity}ImportCandidate } from '@/types/{entity}Import.types';

/**
 * Parse JSON file to source entities
 */
export function parse{Entity}Json(fileContent: string): Source{Entity}[] {
  try {
    const parsed = JSON.parse(fileContent);

    if (!Array.isArray(parsed)) {
      throw new Error('File must contain an array of {entities}');
    }

    // Validate each item
    parsed.forEach((item, index) => {
      if (typeof item !== 'object' || item === null) {
        throw new Error(`Invalid {entity} at index ${index}: must be an object`);
      }

      // Validate required fields
      if (!item.name || typeof item.name !== 'string') {
        throw new Error(`Invalid {entity} at index ${index}: missing or invalid 'name' field`);
      }
    });

    return parsed;
  } catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error(`Invalid JSON format: ${error.message}`);
    }
    throw error;
  }
}

/**
 * Transform source entity to import candidate
 */
export function createImportCandidates(
  source{Entities}: Source{Entity}[]
): {Entity}ImportCandidate[] {
  return source{Entities}.map(source => ({
    name: source.name,
    email: source.email,
    // ... map other fields
    importStatus: 'pending' as const,
  }));
}

/**
 * CSV parsing (if needed)
 */
export function parse{Entity}Csv(fileContent: string): Source{Entity}[] {
  // Use Papa Parse for CSV
  const { data, errors } = Papa.parse<Source{Entity}>(fileContent, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim(),
  });

  if (errors.length > 0) {
    throw new Error(`CSV parsing errors: ${errors.map(e => e.message).join(', ')}`);
  }

  return data;
}
```

---

### Step 3: Business Logic Hook (`hooks/use{Entity}BatchImport.ts`)

```typescript
import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { {entity}ApiClient } from '@/services/{entity}Service';
import { {Entity}ImportCandidate, {Entity}BatchImportResult } from '@/types/{entity}Import.types';

export function use{Entity}BatchImport() {
  const queryClient = useQueryClient();
  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [candidates, setCandidates] = useState<{Entity}ImportCandidate[]>([]);

  /**
   * Update single candidate status
   */
  const updateCandidate = useCallback(
    (index: number, updates: Partial<{Entity}ImportCandidate>) => {
      setCandidates((prev) =>
        prev.map((candidate, i) =>
          i === index ? { ...candidate, ...updates } : candidate
        )
      );
    },
    []
  );

  /**
   * Main import function
   */
  const importCandidates = useCallback(
    async (
      candidatesToImport: {Entity}ImportCandidate[],
      onProgress?: (current: number, total: number) => void
    ): Promise<{Entity}BatchImportResult> => {
      setIsImporting(true);
      setCandidates(candidatesToImport);
      setCurrentIndex(0);

      const result: {Entity}BatchImportResult = {
        total: candidatesToImport.length,
        success: 0,
        updated: 0,
        failed: 0,
        skipped: 0,
      };

      // Sequential processing
      for (let i = 0; i < candidatesToImport.length; i++) {
        const candidate = candidatesToImport[i];
        setCurrentIndex(i);

        // Update status to importing
        updateCandidate(i, { importStatus: 'importing' });
        onProgress?.(i + 1, candidatesToImport.length);

        try {
          // Check if exists (for update logic)
          const exists = candidate.existing{Entity}Id != null;

          if (exists) {
            // Update existing entity
            await {entity}ApiClient.update(
              candidate.existing{Entity}Id,
              {
                name: candidate.name,
                // ... other fields
              }
            );
            updateCandidate(i, { importStatus: 'updated' });
            result.updated++;
          } else {
            // Create new entity
            await {entity}ApiClient.create({
              name: candidate.name,
              // ... other fields
            });
            updateCandidate(i, { importStatus: 'success' });
            result.success++;
          }
        } catch (error) {
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';

          // Check if it's a conflict (409) - treat as skip
          const isConflict =
            errorMessage.includes('409') ||
            errorMessage.includes('Conflict') ||
            errorMessage.includes('already exists');

          if (isConflict) {
            updateCandidate(i, {
              importStatus: 'skipped',
              errorMessage: 'Already exists'
            });
            result.skipped++;
          } else {
            updateCandidate(i, {
              importStatus: 'error',
              errorMessage
            });
            result.failed++;
          }
        }
      }

      // Invalidate cache
      await queryClient.invalidateQueries({ queryKey: ['{entities}'] });

      setIsImporting(false);
      return result;
    },
    [queryClient, updateCandidate]
  );

  return {
    importCandidates,
    isImporting,
    currentIndex,
    candidates,
  };
}
```

---

### Step 4: Modal Component (`components/{domain}/{Entity}BatchImportModal.tsx`)

```typescript
import React, { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  LinearProgress,
  Alert,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  Chip,
  Box,
} from '@mui/material';
import { useDropzone } from 'react-dropzone';
import { CloudUpload, CheckCircle, Error, HourglassEmpty } from '@mui/icons-material';
import { use{Entity}BatchImport } from '@/hooks/use{Entity}BatchImport';
import { parse{Entity}Json, createImportCandidates } from '@/utils/{entity}Import';
import { {Entity}ImportCandidate, ImportStatus } from '@/types/{entity}Import.types';

export function {Entity}BatchImportModal({ open, onClose }: {Entity}BatchImportModalProps) {
  const [parseError, setParseError] = useState<string | null>(null);
  const [importCandidates, setImportCandidates] = useState<{Entity}ImportCandidate[]>([]);
  const [result, setResult] = useState<{Entity}BatchImportResult | null>(null);

  const {
    importCandidates: performImport,
    isImporting,
    currentIndex,
    candidates: updatedCandidates,
  } = use{Entity}BatchImport();

  // File drop handler
  const onDrop = useCallback((acceptedFiles: File[]) => {
    const file = acceptedFiles[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = e.target?.result as string;
        const source{Entities} = parse{Entity}Json(content);
        const candidates = createImportCandidates(source{Entities});

        setImportCandidates(candidates);
        setParseError(null);
      } catch (error) {
        setParseError(error instanceof Error ? error.message : 'Failed to parse file');
        setImportCandidates([]);
      }
    };
    reader.readAsText(file);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/json': ['.json'] },
    multiple: false,
  });

  // Handle import
  const handleImport = async () => {
    const importResult = await performImport(importCandidates);
    setResult(importResult);
  };

  // Reset on close
  const handleClose = () => {
    setImportCandidates([]);
    setParseError(null);
    setResult(null);
    onClose();
  };

  // Get status icon
  const getStatusIcon = (status: ImportStatus) => {
    switch (status) {
      case 'success':
      case 'updated':
        return <CheckCircle color="success" />;
      case 'error':
        return <Error color="error" />;
      case 'importing':
        return <HourglassEmpty color="primary" />;
      case 'skipped':
        return <HourglassEmpty color="warning" />;
      default:
        return <HourglassEmpty color="disabled" />;
    }
  };

  const displayCandidates = updatedCandidates.length > 0 ? updatedCandidates : importCandidates;
  const progress = importCandidates.length > 0
    ? ((currentIndex + 1) / importCandidates.length) * 100
    : 0;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>Import {Entities}</DialogTitle>

      <DialogContent>
        {/* Error Alert */}
        {parseError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {parseError}
          </Alert>
        )}

        {/* Dropzone */}
        {importCandidates.length === 0 && (
          <Box
            {...getRootProps()}
            sx={{
              border: '2px dashed',
              borderColor: isDragActive ? 'primary.main' : 'grey.400',
              borderRadius: 1,
              p: 4,
              textAlign: 'center',
              cursor: 'pointer',
              backgroundColor: isDragActive ? 'action.hover' : 'transparent',
            }}
          >
            <input {...getInputProps()} />
            <CloudUpload sx={{ fontSize: 48, color: 'grey.500', mb: 2 }} />
            <Typography>
              {isDragActive
                ? 'Drop the file here'
                : 'Drag and drop a JSON file, or click to select'}
            </Typography>
          </Box>
        )}

        {/* Preview Table */}
        {displayCandidates.length > 0 && (
          <>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {displayCandidates.length} {entities} ready to import
            </Typography>

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {displayCandidates.map((candidate, index) => (
                  <TableRow key={index}>
                    <TableCell>{candidate.name}</TableCell>
                    <TableCell>{candidate.email || '-'}</TableCell>
                    <TableCell>
                      <Chip
                        icon={getStatusIcon(candidate.importStatus)}
                        label={candidate.errorMessage || candidate.importStatus}
                        size="small"
                        color={
                          candidate.importStatus === 'success' || candidate.importStatus === 'updated'
                            ? 'success'
                            : candidate.importStatus === 'error'
                            ? 'error'
                            : 'default'
                        }
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </>
        )}

        {/* Progress */}
        {isImporting && (
          <Box sx={{ mt: 2 }}>
            <LinearProgress variant="determinate" value={progress} />
            <Typography variant="body2" align="center" sx={{ mt: 1 }}>
              Processing {currentIndex + 1} of {importCandidates.length}...
            </Typography>
          </Box>
        )}

        {/* Result */}
        {result && (
          <Alert severity="success" sx={{ mt: 2 }}>
            Import complete! {result.success} created, {result.updated || 0} updated,{' '}
            {result.failed} failed, {result.skipped} skipped
          </Alert>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={handleClose} disabled={isImporting}>
          {result ? 'Close' : 'Cancel'}
        </Button>
        {importCandidates.length > 0 && !result && (
          <Button
            onClick={handleImport}
            variant="contained"
            disabled={isImporting}
          >
            Import {importCandidates.length} {Entities}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
```

---

## Common Patterns

### Status Management

All imports use the same status lifecycle:

```
pending → importing → (success | updated | error | skipped)
```

### Error Handling

**Conflict Detection** (409 status):
```typescript
const isConflict =
  errorMessage.includes('409') ||
  errorMessage.includes('Conflict') ||
  errorMessage.includes('already exists');

if (isConflict) {
  // Treat as skipped
  result.skipped++;
} else {
  // Treat as error
  result.failed++;
}
```

### Cache Invalidation

After import completes:
```typescript
await queryClient.invalidateQueries({ queryKey: ['{entities}'] });
```

---

## Testing

### Unit Tests (`{entity}Import.test.ts`)

```typescript
import { parse{Entity}Json, createImportCandidates } from './{entity}Import';

describe('{entity}Import utils', () => {
  describe('parse{Entity}Json', () => {
    it('should parse valid JSON', () => {
      const json = '[{"name": "Test"}]';
      const result = parse{Entity}Json(json);
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Test');
    });

    it('should throw on invalid JSON', () => {
      expect(() => parse{Entity}Json('invalid')).toThrow();
    });

    it('should throw on non-array JSON', () => {
      expect(() => parse{Entity}Json('{}')).toThrow('must contain an array');
    });
  });

  describe('createImportCandidates', () => {
    it('should create candidates with pending status', () => {
      const sources = [{ name: 'Test' }];
      const candidates = createImportCandidates(sources);
      expect(candidates[0].importStatus).toBe('pending');
    });
  });
});
```

### Integration Tests (`use{Entity}BatchImport.test.ts`)

```typescript
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { use{Entity}BatchImport } from './use{Entity}BatchImport';

const createWrapper = () => {
  const queryClient = new QueryClient();
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
};

describe('use{Entity}BatchImport', () => {
  it('should import candidates successfully', async () => {
    const { result } = renderHook(() => use{Entity}BatchImport(), {
      wrapper: createWrapper(),
    });

    const candidates = [{ name: 'Test', importStatus: 'pending' as const }];

    let importResult;
    await act(async () => {
      importResult = await result.current.importCandidates(candidates);
    });

    expect(importResult.success).toBeGreaterThan(0);
  });
});
```

---

## Common Pitfalls

### Pitfall 1: Not invalidating cache
**Problem**: UI doesn't update after import
**Solution**: Always invalidate relevant queries:
```typescript
await queryClient.invalidateQueries({ queryKey: ['{entities}'] });
```

### Pitfall 2: Blocking UI during long imports
**Problem**: UI freezes during import
**Solution**: Use sequential processing with progress updates:
```typescript
for (let i = 0; i < candidates.length; i++) {
  // Process item
  onProgress?.(i + 1, candidates.length);
}
```

### Pitfall 3: Not handling duplicates
**Problem**: Import fails on duplicates
**Solution**: Detect conflicts and skip:
```typescript
const isConflict = errorMessage.includes('409');
if (isConflict) {
  result.skipped++;
}
```

---

## Story-Specific Adaptations

### CSV Input (e.g., Participants)

Add Papa Parse for CSV parsing:
```bash
npm install papaparse @types/papaparse
```

Update dropzone accept types:
```typescript
const { getRootProps, getInputProps } = useDropzone({
  accept: { 'text/csv': ['.csv'] },
  // ...
});
```

### Batch Processing (e.g., Sessions)

Group by related entity before import:
```typescript
const sessionsByEvent = groupBy(candidates, 'eventCode');

for (const [eventCode, sessions] of Object.entries(sessionsByEvent)) {
  await {entity}ApiClient.batchCreate(eventCode, sessions);
}
```

### Field Selection (e.g., Events)

Add UI controls for field selection:
```typescript
const [fieldSelection, setFieldSelection] = useState({
  title: false,
  description: false,
  date: true,
});

// Build partial payload
const payload = Object.entries(fieldSelection)
  .filter(([_, selected]) => selected)
  .reduce((acc, [field]) => ({
    ...acc,
    [field]: candidate[field],
  }), {});
```

---

## Related Patterns

- See also: `docs/guides/service-foundation-pattern.md` - Backend service patterns
- See also: `docs/templates/backend/integration-test-pattern.md` - Testing batch APIs

---

**Template Version**: 1.0
**Extracted From**: Stories 3.1 (Companies, Speakers, Events, Sessions)
**Token Savings**: ~1,000 lines per story by referencing template instead of embedding full code
