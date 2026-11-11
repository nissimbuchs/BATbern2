/**
 * ParticipantBatchImportModal end to end in the frontend (Story 4.10c): the real modal, hook,
 * CSV parser and service against the MSW harness. A CSV row is one person with the events they
 * attended; the import sends the people of each event to `POST /events/{eventCode}/participants`
 * (`addParticipants`, mode `people`). Wiring test at the parent: the start button reaches the API
 * with the right bodies, and the per-person status comes back into the table.
 *
 * The older ParticipantBatchImportModal.test.tsx renders the modal with a mocked hook for its
 * display states; this file is the one that runs the import for real.
 */
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api } from '@/test/msw/api';
import { ParticipantBatchImportModal } from './ParticipantBatchImportModal';

/** A CSV with the five metadata columns and the 57 event columns; `events` are the attended ones. */
function csv(rows: Array<{ first: string; last: string; mail: string; events: number[] }>): File {
  const headers = ['Name', 'FirstName', 'LastName', 'BestMail', 'companyKey'];
  for (let i = 1; i <= 57; i++) headers.push(String(i));
  const lines = rows.map((r) => {
    const cells = [`${r.first} ${r.last}`, r.first, r.last, r.mail, ''];
    for (let i = 1; i <= 57; i++) cells.push(r.events.includes(i) ? '1' : '');
    return cells.join(',');
  });
  return new File([[headers.join(','), ...lines].join('\n')], 'participants.csv', {
    type: 'text/csv',
  });
}

describe('ParticipantBatchImportModal import', () => {
  it('should_importPerEvent_and_showEachPersonsStatus_when_startClicked', async () => {
    api.on('addParticipants', ({ body }) => ({
      enrolled: body?.people?.length ?? 0,
      skipped: 0,
      failed: 0,
      results: (body?.people ?? []).map((p) => ({
        email: p.email ?? '',
        outcome: 'REGISTERED' as const,
      })),
    }));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <ParticipantBatchImportModal open onClose={() => {}} />
      </QueryClientProvider>
    );

    const file = csv([
      { first: 'Hugo', last: 'Müster', mail: 'hugo.muster@example.com', events: [3, 12] },
      { first: 'Ines', last: 'Probe', mail: 'ines.probe@example.com', events: [12] },
    ]);
    await userEvent.upload(document.querySelector('input[type="file"]') as HTMLInputElement, file);
    const start = await screen.findByRole('button', { name: /start importing participants/i });
    await userEvent.click(start);

    await waitFor(() => expect(api.calls('addParticipants')).toHaveLength(2));
    const calls = api.calls('addParticipants');
    expect(calls.map((c) => c.params.eventCode)).toEqual(['BATbern3', 'BATbern12']);
    expect(calls[0].body).toEqual({
      people: [
        {
          email: 'hugo.muster@example.com',
          firstName: 'Hugo',
          lastName: 'Müster',
          status: 'ATTENDED',
        },
      ],
    });
    expect(calls[1].body?.people?.map((p) => p.email)).toEqual([
      'hugo.muster@example.com',
      'ines.probe@example.com',
    ]);

    const table = screen.getByRole('table', { name: /participant/i });
    await waitFor(() => expect(within(table).getAllByText(/^success$/i)).toHaveLength(2));
  });
});
