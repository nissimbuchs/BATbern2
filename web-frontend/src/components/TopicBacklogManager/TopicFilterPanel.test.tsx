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
    expect(screen.getAllByText(/Category/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Status/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Sort By/i).length).toBeGreaterThan(0);
  });

  it('should call onFilterChange when category is selected', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(<TopicFilterPanel filters={{}} onFilterChange={onFilterChange} />);

    const categorySelect = screen.getAllByRole('combobox')[0];
    await user.click(categorySelect);
    const technicalOption = await screen.findByText('Technical');
    await user.click(technicalOption);

    expect(onFilterChange).toHaveBeenCalledWith(expect.objectContaining({ category: 'technical' }));
  });

  it('should display current filter values', () => {
    render(
      <TopicFilterPanel
        filters={{ category: 'technical', status: 'AVAILABLE' }}
        onFilterChange={vi.fn()}
      />
    );
    expect(screen.getByText('Technical')).toBeInTheDocument();
    expect(screen.getByText('Available (Green)')).toBeInTheDocument();
  });

  it('should allow clearing filters', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(
      <TopicFilterPanel filters={{ category: 'technical' }} onFilterChange={onFilterChange} />
    );

    // Click on category select and choose "All Categories" to clear
    const categorySelect = screen.getAllByRole('combobox')[0];
    await user.click(categorySelect);
    const allCategoriesOption = await screen.findByText('All Categories');
    await user.click(allCategoriesOption);

    expect(onFilterChange).toHaveBeenCalledWith({ category: undefined });
  });

  it('should support newest first sort option', async () => {
    const user = userEvent.setup();
    const onFilterChange = vi.fn();
    render(<TopicFilterPanel filters={{}} onFilterChange={onFilterChange} />);

    // Click on sort select
    const sortSelect = screen.getAllByRole('combobox')[2]; // Third combobox is sort
    await user.click(sortSelect);

    // Find and click "Newest First" option
    const newestFirstOption = await screen.findByText(/Newest First/i);
    await user.click(newestFirstOption);

    expect(onFilterChange).toHaveBeenCalledWith({ sort: '-createdAt' });
  });
});
