/**
 * Deciding a merge request in the Mail Center closes its conversation at once
 * (PATCH status DONE), instead of leaving it open in the lists until the request feed's next poll.
 * Real hooks and services on the MSW harness; only the user is given (`renderWithAuth`).
 */
import { describe, it, expect } from 'vitest';
import userEvent from '@testing-library/user-event';
import { screen, waitFor } from '@/test/test-utils';
import { renderWithAuth } from '@/test/auth';
import { api } from '@/test/msw/api';
import { ConversationDetail } from '../ConversationDetail';

const REQUEST = '2f6d8c1a-5b7e-4c3d-9a0f-1e2d3c4b5a69';

const profile = (username: string, email: string, hasLogin: boolean) => ({
  username,
  firstName: 'Anna',
  lastName: 'Muster',
  email,
  companyId: null,
  hasLogin,
  roles: ['ATTENDEE'],
  additionalEmails: [],
});

const request = (status: 'OPEN' | 'APPROVED') => ({
  requestId: REQUEST,
  status,
  requesterUsername: 'anna.muster.2',
  sourceUsername: 'anna.muster',
  note: null,
  createdAt: '2026-11-06T20:00:00Z',
  decidedBy: status === 'OPEN' ? null : 'anna.keller',
  decidedAt: status === 'OPEN' ? null : '2026-11-11T10:00:00Z',
  reason: null,
  preview:
    status === 'OPEN'
      ? {
          target: profile('anna.muster.2', 'anna.private@example.com', true),
          source: profile('anna.muster', 'anna@oldco.example.com', false),
          moves: { 'registrations.attendee_username': 3 },
          conflicts: {},
          blockers: [],
          canMerge: true,
          reasons: [],
        }
      : null,
});

describe('ConversationDetail: a decided request closes its conversation', () => {
  it('should set the conversation DONE when the merge request is approved', async () => {
    api.on('getConversation', () => ({
      conversation: {
        conversationKey: 'conv-1',
        eventCode: null,
        audience: 'SYSTEM' as const,
        subject: 'Profil zusammenführen: Anna Muster',
        counterpartAddress: 'anna.private@example.com',
        counterpartName: 'Anna Muster',
        status: 'OPEN' as const,
        assigneeUsername: null,
        via: 'info@batbern.ch',
        lastMessageAt: '2026-11-06T20:00:00Z',
        messageCount: 0,
        mergeRequestId: REQUEST,
        logoRequestId: null,
      },
      messages: [],
    }));
    api.on('getProfileMergeRequest', () => request('OPEN'));
    api.on('approveProfileMergeRequest', () => request('APPROVED'));

    renderWithAuth(<ConversationDetail conversationKey="conv-1" />);
    await userEvent.click(await screen.findByTestId('merge-request-approve'));

    await waitFor(() => expect(api.calls('patchConversation')).toHaveLength(1));
    const patch = api.calls('patchConversation')[0];
    expect(patch.params.conversationKey).toBe('conv-1');
    expect(patch.body).toEqual({ status: 'DONE' });
    expect(api.calls('approveProfileMergeRequest')).toHaveLength(1);
  });
});
