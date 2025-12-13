/**
 * TopicFilterPanel Tests - Focused component tests for filter controls
 */
import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TopicFilterPanel } from './TopicFilterPanel';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, def?: string) => def || key }),
}));

describe('TopicFilterPanel', () => {
  it('should render all filter controls', () => {
    render(<TopicFilterPanel filters={{}} onFilterChange={vi.fn()} />);
    expect(screen.getByLabelText(/Category/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Status/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Sort By/i)).toBeInTheDocument();
  });

  it('should call onFilterChange when category is selected', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(<TopicFilterPanel filters={{}} onFilterChange={onFilterChange} />);

    const categorySelect = screen.getByLabelText(/Category/i);
    await user.click(categorySelect);
    const technicalOption = await screen.findByText('Technical');
    await user.click(technicalOption);

    expect(onFilterChange).toHaveBeenCalledWith(expect.objectContaining({ category: 'technical' }));
  });

  it('should display current filter values', () => {
    render(
      <TopicFilterPanel
        filters={{ category: 'management', status: 'active' }}
        onFilterChange={vi.fn()}
      />
    );
    expect(screen.getByDisplayValue('Management')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Active')).toBeInTheDocument();
  });

  it('should allow clearing filters', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(
      <TopicFilterPanel filters={{ category: 'technical' }} onFilterChange={onFilterChange} />
    );

    const clearButton = screen.getByRole('button', { name: /clear/i });
    await user.click(clearButton);

    expect(onFilterChange).toHaveBeenCalledWith({});
  });
});
