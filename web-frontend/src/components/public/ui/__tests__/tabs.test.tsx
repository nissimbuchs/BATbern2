/**
 * Tabs Component Tests
 * Shadcn UI Component - Tabs wrapper around Radix UI
 *
 * Tests for tabs component used for organizing content into tabs
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../tabs';

describe('Tabs Component', () => {
  test('should_renderTabsList_when_provided', () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
          <TabsTrigger value="tab2">Tab 2</TabsTrigger>
        </TabsList>
      </Tabs>
    );

    expect(screen.getByText('Tab 1')).toBeInTheDocument();
    expect(screen.getByText('Tab 2')).toBeInTheDocument();
  });

  test('should_renderTabsContent_when_provided', () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">
          <div>Content for Tab 1</div>
        </TabsContent>
      </Tabs>
    );

    expect(screen.getByText('Content for Tab 1')).toBeInTheDocument();
  });

  test('should_renderMultipleTabs_when_provided', () => {
    render(
      <Tabs defaultValue="tab1">
        <TabsList>
          <TabsTrigger value="tab1">First</TabsTrigger>
          <TabsTrigger value="tab2">Second</TabsTrigger>
          <TabsTrigger value="tab3">Third</TabsTrigger>
        </TabsList>
      </Tabs>
    );

    expect(screen.getByText('First')).toBeInTheDocument();
    expect(screen.getByText('Second')).toBeInTheDocument();
    expect(screen.getByText('Third')).toBeInTheDocument();
  });

  test('should_renderActiveContent_when_defaultValueSet', () => {
    render(
      <Tabs defaultValue="tab2">
        <TabsList>
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
          <TabsTrigger value="tab2">Tab 2</TabsTrigger>
        </TabsList>
        <TabsContent value="tab1">Content 1</TabsContent>
        <TabsContent value="tab2">Content 2</TabsContent>
      </Tabs>
    );

    // Tab 2 content should be visible by default
    expect(screen.getByText('Content 2')).toBeInTheDocument();
  });

  test('should_applyCustomClassName_when_provided', () => {
    const { container } = render(
      <Tabs defaultValue="tab1">
        <TabsList className="custom-tabs-list">
          <TabsTrigger value="tab1">Tab 1</TabsTrigger>
        </TabsList>
      </Tabs>
    );

    const tabsList = container.querySelector('.custom-tabs-list');
    expect(tabsList).toBeInTheDocument();
  });

  test('should_renderCompleteTabs_when_allPartsProvided', () => {
    render(
      <Tabs defaultValue="info">
        <TabsList>
          <TabsTrigger value="info">Info</TabsTrigger>
          <TabsTrigger value="details">Details</TabsTrigger>
        </TabsList>
        <TabsContent value="info">
          <div>Information content</div>
        </TabsContent>
        <TabsContent value="details">
          <div>Details content</div>
        </TabsContent>
      </Tabs>
    );

    expect(screen.getByText('Info')).toBeInTheDocument();
    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Information content')).toBeInTheDocument();
  });
});
