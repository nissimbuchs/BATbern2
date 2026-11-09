/**
 * MultiTopicHeatMap: a cell's tooltip names the attendance only when it is known (17.0 deferred
 * work: `attendance` is null until the event has taken place; the tooltip read "null attendees").
 */
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { MultiTopicHeatMap, type TopicWithHistory } from './MultiTopicHeatMap';

const year = new Date().getFullYear();

const topic = (title: string, history: TopicWithHistory['usageHistory']): TopicWithHistory =>
  ({
    topicCode: title.toLowerCase(),
    title,
    category: 'technical',
    usageHistory: history,
  }) as TopicWithHistory;

const renderMap = (topics: TopicWithHistory[]) =>
  render(
    <ThemeProvider theme={createTheme()}>
      <MultiTopicHeatMap topics={topics} onTopicSelect={vi.fn()} />
    </ThemeProvider>
  );

describe('MultiTopicHeatMap attendance in the tooltip', () => {
  it('shows the known attendance and leaves out an unknown one', async () => {
    const user = userEvent.setup();
    renderMap([
      topic('Known', [
        {
          eventNumber: 70,
          usedDate: `${year}-02-10T18:00:00Z`,
          attendance: 40,
        },
        {
          eventNumber: 71,
          usedDate: `${year}-03-10T18:00:00Z`,
          attendance: null,
        },
      ]),
      topic('Upcoming', [
        {
          eventNumber: 72,
          usedDate: `${year}-02-20T18:00:00Z`,
          attendance: null,
        },
      ]),
    ]);

    await user.hover(screen.getByText('70, 71'));
    expect(await screen.findByRole('tooltip')).toHaveTextContent('40 attendees');
    await user.unhover(screen.getByText('70, 71'));

    await user.hover(screen.getByText('72'));
    const tooltip = await screen.findByRole('tooltip', { name: /72/ });
    expect(tooltip).not.toHaveTextContent(/null|NaN|attendees/);
  });
});
