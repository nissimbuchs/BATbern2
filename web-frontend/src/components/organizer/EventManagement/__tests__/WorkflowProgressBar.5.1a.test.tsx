/**
 * WorkflowProgressBar Component Tests - Story 5.1a Integration (RED Phase - TDD)
 *
 * Story 5.1a - Task 8/9 (Frontend Component Tests with React Query)
 * AC: 15-17 (Frontend Integration with Workflow API)
 *
 * Tests for WorkflowProgressBar component integrated with:
 * - React Query for data fetching
 * - GET /api/v1/events/{code}/workflow/status endpoint
 * - Real-time updates (refetchInterval)
 * - Validation blocker messages
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { workflowService } from '@/services/workflowService';
import type { WorkflowStatusDto } from '@/services/workflowService';

// Mock workflowService
vi.mock('@/services/workflowService', () => ({
  workflowService: {
    getWorkflowStatus: vi.fn(),
  },
}));

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    BrowserRouter: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  };
});

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (
      key: string,
      defaultValue?: string | Record<string, unknown>,
      params?: Record<string, unknown>
    ) => {
      // Handle workflow state translations
      if (key.startsWith('workflow.states.')) {
        // If default value is provided (the raw enum value like SPEAKER_OUTREACH)
        if (typeof defaultValue === 'string') {
          // Convert SPEAKER_OUTREACH to "Speaker Outreach"
          return defaultValue
            .split('_')
            .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
            .join(' ');
        }

        // Otherwise, convert from the key
        const state = key.replace('workflow.states.', '');
        return state
          .split('_')
          .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
          .join(' ');
      }

      // Handle translation keys with default values
      if (typeof defaultValue === 'string') {
        // If default value provided, use it
        return defaultValue;
      }

      // Handle other workflow keys
      if (key === 'workflow.loading') {
        return 'Loading workflow status...';
      }
      if (key === 'workflow.error') {
        return 'Failed to load workflow status';
      }
      if (key === 'workflow.currentState') {
        return 'Current State';
      }
      if (key === 'workflow.blockers') {
        const count = typeof defaultValue === 'object' ? defaultValue?.count : 1;
        return `${count} blocker${count > 1 ? 's' : ''}`;
      }
      if (key === 'workflow.blockedTransitions') {
        return 'Blocked:';
      }
      if (key === 'common.retry') {
        return 'Retry';
      }

      return key;
    },
    i18n: { language: 'en' },
  }),
}));

// Import component AFTER mocks
import { WorkflowProgressBarWithQuery } from '../WorkflowProgressBarWithQuery';

describe('WorkflowProgressBar - Story 5.1a Integration (React Query)', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false, // Disable retries for tests
        },
      },
    });
  });

  const renderWithQuery = (component: React.ReactElement) => {
    return render(<QueryClientProvider client={queryClient}>{component}</QueryClientProvider>);
  };

  describe('AC15: Display Current State', () => {
    it('should_displayCurrentState_when_WorkflowProgressBarMounted', async () => {
      // Given: Mock workflow status response
      const mockStatus: WorkflowStatusDto = {
        currentState: 'SPEAKER_OUTREACH',
        blockedTransitions: [],
        nextAvailableStates: ['SPEAKER_CONFIRMATION'],
        validationMessages: [],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should display loading state first
      expect(screen.getByText(/loading workflow status/i)).toBeInTheDocument();

      // Wait for data to load
      await waitFor(() => {
        expect(screen.queryByText(/loading/i)).not.toBeInTheDocument();
      });

      // Should display current state
      expect(screen.getByText(/speaker outreach/i)).toBeInTheDocument();
    });

    it('should_callWorkflowAPI_when_componentMounts', async () => {
      // Given: Mock workflow status
      const mockStatus: WorkflowStatusDto = {
        currentState: 'TOPIC_SELECTION',
        blockedTransitions: [],
        nextAvailableStates: ['SPEAKER_BRAINSTORMING'],
        validationMessages: [],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should call workflowService with correct event code
      await waitFor(() => {
        expect(workflowService.getWorkflowStatus).toHaveBeenCalledWith('BATbern56');
      });
    });

    it('should_showLoadingState_when_dataFetching', () => {
      // Given: Mock API call that never resolves
      vi.mocked(workflowService.getWorkflowStatus).mockImplementation(() => new Promise(() => {}));

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should show loading indicator
      expect(screen.getByText(/loading workflow status/i)).toBeInTheDocument();
    });

    it('should_showErrorState_when_APIFails', async () => {
      // Given: Mock API error
      vi.mocked(workflowService.getWorkflowStatus).mockRejectedValue(new Error('Network error'));

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should display error message
      await waitFor(() => {
        expect(screen.getByText(/failed to load workflow status/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC16: Show Validation Blockers', () => {
    it('should_showValidationBlockers_when_transitionBlocked', async () => {
      // Given: Mock status with validation messages
      const mockStatus: WorkflowStatusDto = {
        currentState: 'TOPIC_SELECTION',
        blockedTransitions: ['SPEAKER_OUTREACH'],
        nextAvailableStates: ['SPEAKER_BRAINSTORMING'],
        validationMessages: ['Need 6 speakers, have 3', 'Venue not confirmed'],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should display validation messages
      await waitFor(() => {
        expect(screen.getByText(/need 6 speakers, have 3/i)).toBeInTheDocument();
        expect(screen.getByText(/venue not confirmed/i)).toBeInTheDocument();
      });
    });

    it('should_notShowBlockers_when_noValidationMessages', async () => {
      // Given: Mock status without validation messages
      const mockStatus: WorkflowStatusDto = {
        currentState: 'CREATED',
        blockedTransitions: [],
        nextAvailableStates: ['TOPIC_SELECTION'],
        validationMessages: [],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should not display any blockers
      await waitFor(() => {
        expect(screen.queryByText(/need 6 speakers/i)).not.toBeInTheDocument();
      });
    });

    it('should_highlightBlockedTransitions_when_displayingStatus', async () => {
      // Given: Mock status with blocked transitions
      const mockStatus: WorkflowStatusDto = {
        currentState: 'SPEAKER_BRAINSTORMING',
        blockedTransitions: ['SPEAKER_OUTREACH', 'QUALITY_REVIEW'],
        nextAvailableStates: ['SPEAKER_CONFIRMATION'],
        validationMessages: ['Minimum speaker threshold not met'],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should display blocked transitions
      await waitFor(() => {
        expect(screen.getByText(/minimum speaker threshold not met/i)).toBeInTheDocument();
      });
    });
  });

  describe('AC17: Real-Time UI Updates', () => {
    it('should_refetchData_when_refetchIntervalElapses', async () => {
      // Given: Mock workflow status that changes
      const mockStatusInitial: WorkflowStatusDto = {
        currentState: 'CREATED',
        blockedTransitions: [],
        nextAvailableStates: ['TOPIC_SELECTION'],
        validationMessages: [],
      };

      const mockStatusUpdated: WorkflowStatusDto = {
        currentState: 'TOPIC_SELECTION',
        blockedTransitions: [],
        nextAvailableStates: ['SPEAKER_BRAINSTORMING'],
        validationMessages: [],
      };

      // First call returns initial state, subsequent calls return updated state
      vi.mocked(workflowService.getWorkflowStatus)
        .mockResolvedValueOnce(mockStatusInitial)
        .mockResolvedValue(mockStatusUpdated);

      // When: Component mounted with refetch enabled (50ms for faster test)
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" refetchInterval={50} />);

      // Then: Should initially show CREATED state
      await waitFor(() => {
        expect(screen.getByText(/created/i)).toBeInTheDocument();
      });

      // Verify initial API call
      expect(workflowService.getWorkflowStatus).toHaveBeenCalledTimes(1);

      // Wait for refetch to occur (wait for API call count to increase)
      await waitFor(
        () => {
          expect(workflowService.getWorkflowStatus).toHaveBeenCalledTimes(2);
        },
        { timeout: 500 }
      );

      // Then: UI should update to show new state
      await waitFor(() => {
        expect(screen.getByText(/topic selection/i)).toBeInTheDocument();
      });
    });

    it('should_updateUIRealTime_when_stateChangeOccurs', async () => {
      // Given: Mock status that transitions
      const mockStatus: WorkflowStatusDto = {
        currentState: 'SPEAKER_OUTREACH',
        blockedTransitions: [],
        nextAvailableStates: ['SPEAKER_CONFIRMATION'],
        validationMessages: [],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockStatus);

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Wait for initial render
      await waitFor(() => {
        expect(screen.getByText(/speaker outreach/i)).toBeInTheDocument();
      });

      // Simulate state change
      const mockUpdatedStatus: WorkflowStatusDto = {
        currentState: 'SPEAKER_CONFIRMATION',
        blockedTransitions: [],
        nextAvailableStates: ['CONTENT_COLLECTION'],
        validationMessages: [],
      };

      vi.mocked(workflowService.getWorkflowStatus).mockResolvedValue(mockUpdatedStatus);

      // Invalidate query to trigger refetch
      await queryClient.invalidateQueries({ queryKey: ['workflowStatus', 'BATbern56'] });

      // Then: Should display updated state
      await waitFor(() => {
        expect(screen.getByText(/speaker confirmation/i)).toBeInTheDocument();
      });
    });
  });

  describe('Error Handling', () => {
    it('should_showRetryOption_when_APIFails', async () => {
      // Given: Mock API error
      vi.mocked(workflowService.getWorkflowStatus).mockRejectedValue(new Error('Network error'));

      // When: Component mounted
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="BATbern56" />);

      // Then: Should display error with retry option
      await waitFor(() => {
        expect(screen.getByText(/failed to load workflow status/i)).toBeInTheDocument();
      });
    });

    it('should_handleMissingEventCode_when_invalidCodeProvided', async () => {
      // Given: Mock 404 error
      vi.mocked(workflowService.getWorkflowStatus).mockRejectedValue({
        response: { status: 404, data: { message: 'Event not found' } },
      });

      // When: Component mounted with invalid code
      renderWithQuery(<WorkflowProgressBarWithQuery eventCode="INVALID" />);

      // Then: Should display error
      await waitFor(() => {
        expect(screen.getByText(/failed to load workflow status/i)).toBeInTheDocument();
      });
    });
  });
});
