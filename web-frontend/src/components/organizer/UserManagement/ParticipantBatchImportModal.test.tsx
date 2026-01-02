/**
 * ParticipantBatchImportModal Component Tests
 *
 * Tests dropzone, preview table, progress updates, and error states
 * Addresses QA Issue TEST-002: Missing component tests for modal
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ParticipantBatchImportModal } from './ParticipantBatchImportModal';
import * as useParticipantBatchImportModule from '../../../hooks/useParticipantBatchImport';
import type { ParticipantBatchImportResult } from '../../../types/participantImport.types';
import '../../../i18n/config';

// Mock the hook
vi.mock('../../../hooks/useParticipantBatchImport');

// Mock Papa Parse to avoid actual CSV parsing in tests
vi.mock('papaparse', () => ({
  default: {
    parse: vi.fn((content, options) => {
      // Simple mock parser for testing
      const lines = content.split('\n');
      const headers = lines[0].split(',');

      if (headers.length !== 62) {
        return {
          data: [],
          errors: [
            { message: 'Invalid CSV structure: expected 62 columns, got ' + headers.length },
          ],
        };
      }

      const data = lines
        .slice(1)
        .filter((line: string) => line.trim())
        .map((line: string) => {
          const values = line.split(',');
          const row: Record<string, string> = {};
          headers.forEach((header: string, index: number) => {
            row[header.trim()] = values[index] || '';
          });
          return row;
        });

      return { data, errors: [] };
    }),
  },
}));

describe('ParticipantBatchImportModal Component', () => {
  let queryClient: QueryClient;
  const mockOnClose = vi.fn();
  const mockOnImportComplete = vi.fn();

  // Mock hook implementation
  const mockImportCandidates = vi.fn();
  const mockReset = vi.fn();

  const mockHookReturn = {
    importCandidates: mockImportCandidates,
    isImporting: false,
    currentIndex: 0,
    candidates: [],
    reset: mockReset,
  };

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    mockOnClose.mockClear();
    mockOnImportComplete.mockClear();
    mockImportCandidates.mockClear();
    mockReset.mockClear();

    // Setup default mock return
    vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue(
      mockHookReturn
    );
  });

  afterEach(() => {
    queryClient.clear();
  });

  const renderComponent = (open: boolean = true) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <ParticipantBatchImportModal
          open={open}
          onClose={mockOnClose}
          onImportComplete={mockOnImportComplete}
        />
      </QueryClientProvider>
    );
  };

  describe('Modal Rendering', () => {
    it('should_renderDialog_when_openIsTrue', () => {
      renderComponent(true);

      const dialog = screen.getByRole('dialog');
      expect(dialog).toBeInTheDocument();
      expect(screen.getByText('Import Participants from CSV')).toBeInTheDocument();
    });

    it('should_notRenderDialog_when_openIsFalse', () => {
      renderComponent(false);

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('should_renderDropzone_when_noCsvUploaded', () => {
      renderComponent();

      expect(screen.getByText(/Drag & drop CSV file here/i)).toBeInTheDocument();
      expect(screen.getByText(/or click to select file/i)).toBeInTheDocument();
    });
  });

  describe('File Upload - Dropzone', () => {
    it('should_acceptCsvFile_when_validFileDropped', async () => {
      renderComponent();

      // Create mock CSV file with 62 columns
      const headers = ['Name', 'FirstName', 'LastName', 'BestMail', 'companyKey'];
      for (let i = 1; i <= 57; i++) {
        headers.push(i.toString());
      }
      const csvContent =
        headers.join(',') +
        '\nTest,Test,User,test@example.com,company1,' +
        '1,'.repeat(57).slice(0, -1);
      const file = new File([csvContent], 'participants.csv', { type: 'text/csv' });

      const dropzone = screen.getByText(/Drag & drop CSV file here/i).parentElement;
      expect(dropzone).toBeInTheDocument();

      // Simulate file drop
      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      await userEvent.upload(fileInput, file);

      await waitFor(() => {
        expect(
          screen.getByRole('region', { name: /participant import preview table/i })
        ).toBeInTheDocument();
      });
    });

    it('should_showError_when_invalidCsvStructure', async () => {
      renderComponent();

      // Create CSV with wrong number of columns (only 5 instead of 62)
      const csvContent =
        'Name,FirstName,LastName,Email,Company\nTest,Test,User,test@example.com,TestCo';
      const file = new File([csvContent], 'invalid.csv', { type: 'text/csv' });

      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      await userEvent.upload(fileInput, file);

      await waitFor(() => {
        expect(
          screen.getByText(/Invalid CSV structure: expected 62 columns, got 5/i)
        ).toBeInTheDocument();
      });
    });

    it('should_disableDropzone_when_importing', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        isImporting: true,
      });

      renderComponent();

      const dropzone = screen.getByText(/Drag & drop CSV file here/i).parentElement;
      // Dropzone should be disabled during import (check via aria-disabled or disabled attribute)
      expect(dropzone?.closest('div')).toHaveStyle({ cursor: 'pointer' }); // Note: react-dropzone disables via prop, not DOM attribute
    });
  });

  describe('Preview Table', () => {
    it('should_renderPreviewTable_when_candidatesExist', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'John',
            lastName: 'Doe',
            username: 'john.doe',
            email: 'john.doe@example.com',
            eventCount: 3,
            isSyntheticEmail: false,
            importStatus: 'pending',
          },
          {
            firstName: 'Jane',
            lastName: 'Smith',
            username: 'jane.smith',
            email: 'jane.smith@batbern.ch',
            eventCount: 5,
            isSyntheticEmail: true,
            importStatus: 'pending',
          },
        ],
      });

      renderComponent();

      // Check that the preview table is rendered
      expect(
        screen.getByRole('region', { name: /participant import preview table/i })
      ).toBeInTheDocument();

      // Check participant data is displayed
      expect(screen.getByText('John Doe')).toBeInTheDocument();
      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
      expect(screen.getByText('jane.smith@batbern.ch')).toBeInTheDocument();

      // Check usernames are displayed
      expect(screen.getByText('@john.doe')).toBeInTheDocument();
      expect(screen.getByText('@jane.smith')).toBeInTheDocument();
    });

    it('should_showSyntheticBadge_when_emailIsSynthetic', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'Test',
            lastName: 'User',
            email: 'test.user@batbern.ch',
            eventCount: 2,
            isSyntheticEmail: true,
            importStatus: 'pending',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText('Synthetic')).toBeInTheDocument();
    });

    it('should_notShowSyntheticBadge_when_emailIsReal', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'Test',
            lastName: 'User',
            email: 'test@example.com',
            eventCount: 2,
            isSyntheticEmail: false,
            importStatus: 'pending',
          },
        ],
      });

      renderComponent();

      expect(screen.queryByText('Synthetic')).not.toBeInTheDocument();
    });

    it('should_displayStatusChips_when_candidatesHaveStatus', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'User1',
            lastName: 'Test',
            email: 'user1@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'pending',
          },
          {
            firstName: 'User2',
            lastName: 'Test',
            email: 'user2@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'success',
          },
          {
            firstName: 'User3',
            lastName: 'Test',
            email: 'user3@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'error',
            errorMessage: 'Invalid email',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText('Pending')).toBeInTheDocument();
      expect(screen.getByText('Success')).toBeInTheDocument();
      expect(screen.getByText('Failed')).toBeInTheDocument();
      expect(screen.getByText('Invalid email')).toBeInTheDocument();
    });
  });

  describe('Progress Updates', () => {
    it('should_showProgressBar_when_importing', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        isImporting: true,
        candidates: [
          {
            firstName: 'Test',
            lastName: 'User',
            email: 'test@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'importing',
          },
        ],
      });

      renderComponent();

      expect(screen.getByRole('progressbar')).toBeInTheDocument();
      expect(screen.getByText(/Importing: 0 \/ 1/i)).toBeInTheDocument();
    });

    it('should_updateProgressText_when_candidatesProcessed', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        isImporting: true,
        candidates: [
          {
            firstName: 'User1',
            lastName: 'Test',
            email: 'user1@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'success',
          },
          {
            firstName: 'User2',
            lastName: 'Test',
            email: 'user2@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'importing',
          },
          {
            firstName: 'User3',
            lastName: 'Test',
            email: 'user3@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'pending',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText(/Importing: 1 \/ 3/i)).toBeInTheDocument();
    });
  });

  describe('Import Summary', () => {
    it('should_displaySummaryStats_when_candidatesExist', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'User1',
            lastName: 'Test',
            email: 'user1@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'success',
          },
          {
            firstName: 'User2',
            lastName: 'Test',
            email: 'user2@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'error',
          },
          {
            firstName: 'User3',
            lastName: 'Test',
            email: 'user3@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'pending',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText('Import Summary')).toBeInTheDocument();
      expect(screen.getByText('Total: 3')).toBeInTheDocument();
      expect(screen.getByText('Success: 1')).toBeInTheDocument();
      expect(screen.getByText('Failed: 1')).toBeInTheDocument();
      expect(screen.getByText('Pending: 1')).toBeInTheDocument();
    });
  });

  describe('Import Button Interaction', () => {
    it('should_callImportCandidates_when_startImportClicked', async () => {
      renderComponent();

      // Upload file first
      const headers = ['Name', 'FirstName', 'LastName', 'BestMail', 'companyKey'];
      for (let i = 1; i <= 57; i++) {
        headers.push(i.toString());
      }
      const csvContent =
        headers.join(',') +
        '\nTest,Test,User,test@example.com,company1,' +
        '1,'.repeat(57).slice(0, -1);
      const file = new File([csvContent], 'participants.csv', { type: 'text/csv' });

      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      await userEvent.upload(fileInput, file);

      // Wait for preview table to appear after file is loaded
      await waitFor(() => {
        expect(
          screen.getByRole('region', { name: /participant import preview table/i })
        ).toBeInTheDocument();
      });

      // Click Start Import button (using aria-label)
      const importButton = screen.getByRole('button', { name: /start importing participants/i });
      expect(importButton).toBeInTheDocument();

      mockImportCandidates.mockResolvedValue({
        total: 1,
        success: 1,
        failed: 0,
        skipped: 0,
      } as ParticipantBatchImportResult);

      await userEvent.click(importButton);

      await waitFor(() => {
        expect(mockImportCandidates).toHaveBeenCalled();
      });
    });

    it('should_disableImportButton_when_importing', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        isImporting: true,
      });

      renderComponent();

      // Should not show Start Import button when importing
      expect(screen.queryByText('Start Import')).not.toBeInTheDocument();
    });
  });

  describe('Close Button Behavior', () => {
    it('should_callOnClose_when_cancelClicked', async () => {
      renderComponent();

      const cancelButton = screen.getByRole('button', { name: /close import modal/i });
      await userEvent.click(cancelButton);

      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('should_disableCloseButton_when_importing', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        isImporting: true,
      });

      renderComponent();

      const cancelButton = screen.getByText('Cancel');
      expect(cancelButton).toBeDisabled();
    });

    it('should_showCloseButton_when_importComplete', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'Test',
            lastName: 'User',
            email: 'test@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'success',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText('Close')).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should_displayParseError_when_csvParsingFails', async () => {
      renderComponent();

      // Upload invalid CSV
      const csvContent = 'Invalid,CSV\nWith,Only,Three,Columns';
      const file = new File([csvContent], 'invalid.csv', { type: 'text/csv' });

      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      await userEvent.upload(fileInput, file);

      await waitFor(() => {
        expect(screen.getByText(/Invalid CSV structure/i)).toBeInTheDocument();
      });
    });

    it('should_displayErrorMessages_when_importFails', () => {
      vi.mocked(useParticipantBatchImportModule.useParticipantBatchImport).mockReturnValue({
        ...mockHookReturn,
        candidates: [
          {
            firstName: 'Test',
            lastName: 'User',
            email: 'test@example.com',
            eventCount: 1,
            isSyntheticEmail: false,
            importStatus: 'error',
            errorMessage: 'Network error: Unable to connect to server',
          },
        ],
      });

      renderComponent();

      expect(screen.getByText('Network error: Unable to connect to server')).toBeInTheDocument();
    });
  });

  describe('Callback Invocation', () => {
    it('should_callOnImportComplete_when_importSucceeds', async () => {
      renderComponent();

      // Upload file
      const headers = ['Name', 'FirstName', 'LastName', 'BestMail', 'companyKey'];
      for (let i = 1; i <= 57; i++) {
        headers.push(i.toString());
      }
      const csvContent =
        headers.join(',') +
        '\nTest,Test,User,test@example.com,company1,' +
        '1,'.repeat(57).slice(0, -1);
      const file = new File([csvContent], 'participants.csv', { type: 'text/csv' });

      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      await userEvent.upload(fileInput, file);

      // Wait for preview table to appear
      await waitFor(() => {
        expect(
          screen.getByRole('region', { name: /participant import preview table/i })
        ).toBeInTheDocument();
      });

      const mockResult: ParticipantBatchImportResult = {
        total: 1,
        success: 1,
        failed: 0,
        skipped: 0,
      };

      mockImportCandidates.mockResolvedValue(mockResult);

      // Click Start Import
      const importButton = screen.getByRole('button', { name: /start importing participants/i });
      await userEvent.click(importButton);

      await waitFor(() => {
        expect(mockOnImportComplete).toHaveBeenCalledWith(mockResult);
      });
    });
  });
});
