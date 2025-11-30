/**
 * Tests for CompanyBatchImportModal Component
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CompanyBatchImportModal } from './CompanyBatchImportModal';

// Mock i18n
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const translations: Record<string, string> = {
        'company.batchImport.title': 'Import Companies from JSON',
        'company.batchImport.dropzone': 'Drop JSON file here or click to upload',
        'company.batchImport.dropzoneActive': 'Drop the JSON file here',
        'company.batchImport.preview': `Preview (${params?.count || 0} companies)`,
        'company.batchImport.importButton': 'Import All',
        'company.batchImport.importButtonWithCount': `Import ${params?.count || 0} Companies`,
        'company.batchImport.allSkipped': 'All Skipped',
        'company.batchImport.cancelButton': 'Cancel',
        'company.batchImport.noCompanies': 'No companies found in the JSON file.',
        'company.batchImport.status.pending': 'Pending',
        'company.batchImport.status.importing': 'Importing...',
        'company.batchImport.status.success': 'Imported',
        'company.batchImport.status.error': 'Error',
        'company.batchImport.status.skipped': 'Skipped (exists)',
        'company.batchImport.progress': `Importing ${params?.current || 0} of ${params?.total || 0}...`,
        'company.batchImport.complete': `Import complete: ${params?.success || 0} succeeded, ${params?.failed || 0} failed, ${params?.skipped || 0} skipped`,
        'company.batchImport.checkingDuplicates': 'Checking for existing companies...',
        'company.batchImport.duplicatesFound': `${params?.count || 0} of ${params?.total || 0} companies already exist and will be skipped.`,
        'company.batchImport.columns.logo': 'Logo',
        'company.batchImport.columns.displayName': 'Display Name',
        'company.batchImport.columns.name': 'API Name',
        'company.batchImport.columns.industry': 'Industry',
        'company.batchImport.columns.website': 'Website',
        'company.batchImport.columns.status': 'Status',
        'company.batchImport.errors.invalidFile': 'Invalid file type. Please upload a JSON file.',
        'company.batchImport.errors.parseError': `Failed to parse JSON file: ${params?.error || ''}`,
        'actions.close': 'Close',
      };
      return translations[key] || key;
    },
  }),
}));

// Mock companyApiClient
vi.mock('@/services/api/companyApi', () => ({
  companyApiClient: {
    createCompany: vi.fn(),
    getCompanies: vi.fn(),
  },
}));

// Mock useCompanies hook
vi.mock('@/hooks/useCompanies/useCompanies', () => ({
  useCompanies: vi.fn(() => ({
    data: {
      data: [],
      pagination: {
        page: 1,
        limit: 1000,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrev: false,
      },
    },
    isLoading: false,
    error: null,
  })),
}));

import { companyApiClient } from '@/services/api/companyApi';
import { useCompanies } from '@/hooks/useCompanies/useCompanies';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const validCompaniesJson = JSON.stringify([
  {
    id: 'sbb',
    displayName: 'SBB CFF FFS',
    logo: 'sbb.jpg',
    url: 'https://www.sbb.ch',
    speakerCount: 36,
    logoFilePath: '/path/to/sbb.jpg',
    status: 'complete',
    industry: 'Transportation',
  },
  {
    id: 'mobiliar',
    displayName: 'Die Mobiliar',
    logo: 'mobiliar.jpg',
    url: 'https://www.mobiliar.ch',
    speakerCount: 22,
    logoFilePath: '/path/to/mobiliar.jpg',
    status: 'complete',
    industry: 'Insurance',
  },
]);

function createMockFile(content: string, name = 'companies.json'): File {
  return new File([content], name, { type: 'application/json' });
}

describe('CompanyBatchImportModal', () => {
  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    onImportComplete: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    (companyApiClient.createCompany as ReturnType<typeof vi.fn>).mockResolvedValue({
      name: 'sbb',
      displayName: 'SBB CFF FFS',
      isVerified: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
  });

  it('should_renderTitle_when_modalOpened', () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Import Companies from JSON')).toBeInTheDocument();
  });

  it('should_showDropzone_when_noFileUploaded', () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Drop JSON file here or click to upload')).toBeInTheDocument();
  });

  it('should_notRender_when_modalClosed', () => {
    render(<CompanyBatchImportModal {...defaultProps} open={false} />, {
      wrapper: createWrapper(),
    });

    expect(screen.queryByText('Import Companies from JSON')).not.toBeInTheDocument();
  });

  it('should_callOnClose_when_cancelButtonClicked', async () => {
    const onClose = vi.fn();
    render(<CompanyBatchImportModal {...defaultProps} onClose={onClose} />, {
      wrapper: createWrapper(),
    });

    await userEvent.click(screen.getByText('Cancel'));

    expect(onClose).toHaveBeenCalled();
  });

  it('should_showPreview_when_validJsonFileDropped', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText('Preview (2 companies)')).toBeInTheDocument();
    });

    expect(screen.getByText('SBB CFF FFS')).toBeInTheDocument();
    expect(screen.getByText('Die Mobiliar')).toBeInTheDocument();
  });

  it('should_showError_when_invalidJsonDropped', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile('not valid json');
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText(/Failed to parse JSON file/)).toBeInTheDocument();
    });
  });

  it('should_showError_when_jsonHasNoCompanies', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile('[]');
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText('No companies found in the JSON file.')).toBeInTheDocument();
    });
  });

  it('should_showImportButton_when_companiesLoaded', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      // Now shows count: "Import 2 Companies"
      expect(screen.getByText('Import 2 Companies')).toBeInTheDocument();
    });
  });

  it('should_showTransformedApiName_when_companiesLoaded', async () => {
    const companiesWithSpecialId = JSON.stringify([
      {
        id: 'isc-ejpd',
        displayName: 'ISC-EJPD',
        logo: null,
        url: 'https://www.isc.admin.ch',
        speakerCount: 4,
        logoFilePath: null,
        status: 'complete',
      },
    ]);

    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(companiesWithSpecialId);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      // The transformed name should show without hyphens
      expect(screen.getByText('iscejpd')).toBeInTheDocument();
    });
  });

  it('should_callCreateCompany_when_importButtonClicked', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText('Import 2 Companies')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('Import 2 Companies'));

    await waitFor(() => {
      expect(companyApiClient.createCompany).toHaveBeenCalledTimes(2);
    });
  });

  it('should_showSuccessResult_when_importComplete', async () => {
    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText('Import 2 Companies')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('Import 2 Companies'));

    await waitFor(() => {
      expect(
        screen.getByText(/Import complete: 2 succeeded, 0 failed, 0 skipped/)
      ).toBeInTheDocument();
    });
  });

  it('should_showSkippedStatus_when_companyAlreadyExists', async () => {
    (companyApiClient.createCompany as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('409: Company already exists')
    );

    const singleCompanyJson = JSON.stringify([
      {
        id: 'sbb',
        displayName: 'SBB',
        logo: null,
        url: 'https://sbb.ch',
        speakerCount: 1,
        logoFilePath: null,
        status: 'complete',
      },
    ]);

    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(singleCompanyJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    await waitFor(() => {
      expect(screen.getByText('Import 1 Companies')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByText('Import 1 Companies'));

    await waitFor(() => {
      expect(
        screen.getByText(/Import complete: 0 succeeded, 0 failed, 1 skipped/)
      ).toBeInTheDocument();
    });
  });

  it('should_preSkipDuplicates_when_companiesAlreadyExist', async () => {
    // Mock useCompanies to return existing companies
    (useCompanies as ReturnType<typeof vi.fn>).mockReturnValue({
      data: {
        data: [
          { name: 'sbb', displayName: 'SBB', isVerified: false, createdAt: '', updatedAt: '' },
        ],
        pagination: {
          page: 1,
          limit: 1000,
          totalItems: 1,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      },
      isLoading: false,
      error: null,
    });

    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson); // Contains sbb and mobiliar
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    // Should show 1 company to import (mobiliar), sbb is pre-skipped
    await waitFor(() => {
      expect(screen.getByText('Import 1 Companies')).toBeInTheDocument();
    });

    // Should show duplicates found alert
    expect(screen.getByText(/1 of 2 companies already exist/)).toBeInTheDocument();
  });

  it('should_showAllSkipped_when_allCompaniesExist', async () => {
    // Mock useCompanies to return all companies as existing
    (useCompanies as ReturnType<typeof vi.fn>).mockReturnValue({
      data: {
        data: [
          { name: 'sbb', displayName: 'SBB', isVerified: false, createdAt: '', updatedAt: '' },
          {
            name: 'mobiliar',
            displayName: 'Mobiliar',
            isVerified: false,
            createdAt: '',
            updatedAt: '',
          },
        ],
        pagination: {
          page: 1,
          limit: 1000,
          totalItems: 2,
          totalPages: 1,
          hasNext: false,
          hasPrev: false,
        },
      },
      isLoading: false,
      error: null,
    });

    render(<CompanyBatchImportModal {...defaultProps} />, {
      wrapper: createWrapper(),
    });

    const file = createMockFile(validCompaniesJson);
    const dropzone = screen.getByTestId('json-dropzone');
    const input = dropzone.querySelector('input')!;

    await waitFor(() => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    // Button should show "All Skipped" and be disabled
    await waitFor(() => {
      const button = screen.getByText('All Skipped');
      expect(button).toBeInTheDocument();
      expect(button.closest('button')).toBeDisabled();
    });
  });
});
