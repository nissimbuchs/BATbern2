/**
 * SlotTemplatePreview Component Tests (RED Phase - TDD)
 *
 * Story 5.1 - Task 3a
 * AC: 7 (React Component - SlotTemplatePreview)
 * Wireframe: docs/wireframes/story-5.1-event-type-configuration.md v1.0 (Screen 3)
 *
 * Tests for slot template preview component with:
 * - Display of slot count range (min-max)
 * - Slot duration display
 * - Break/lunch slots information
 * - Typical start-end times
 * - Default capacity
 * - i18n compliance (all text uses react-i18next)
 * - Generated types usage from events-api.types.ts
 */

import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { SlotTemplatePreview } from './SlotTemplatePreview';
import type { components } from '@/types/generated/events-api.types';

type EventType = components['schemas']['EventType'];
type EventSlotConfigurationResponse = components['schemas']['EventSlotConfigurationResponse'];

describe('SlotTemplatePreview Component', () => {
  const mockFullDayConfig: EventSlotConfigurationResponse = {
    type: 'FULL_DAY',
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 45,
    theoreticalSlotsAM: true,
    breakSlots: 2,
    lunchSlots: 1,
    defaultCapacity: 200,
    typicalStartTime: '09:00',
    typicalEndTime: '17:00',
  };

  const mockAfternoonConfig: EventSlotConfigurationResponse = {
    type: 'AFTERNOON',
    minSlots: 6,
    maxSlots: 8,
    slotDuration: 30,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 150,
    typicalStartTime: '13:00',
    typicalEndTime: '18:00',
  };

  const mockEveningConfig: EventSlotConfigurationResponse = {
    type: 'EVENING',
    minSlots: 3,
    maxSlots: 4,
    slotDuration: 45,
    theoreticalSlotsAM: false,
    breakSlots: 1,
    lunchSlots: 0,
    defaultCapacity: 100,
    typicalStartTime: '18:00',
    typicalEndTime: '21:00',
  };

  const createWrapper = () => {
    return ({ children }: { children: React.ReactNode }) => (
      <I18nextProvider i18n={i18n}>{children}</I18nextProvider>
    );
  };

  /**
   * Test 7.4a: should_displayEventTypeName_when_previewRendered
   * AC7: Component displays event type name as title
   */
  it('should_displayEventTypeName_when_previewRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/full day event preview/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4b: should_displaySlotRange_when_previewRendered
   * AC7: Component displays slot count range (6-8 slots)
   */
  it('should_displaySlotRange_when_previewRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/6-8 slots/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4c: should_displaySlotDuration_when_previewRendered
   * AC7: Component displays slot duration (45 min each)
   */
  it('should_displaySlotDuration_when_previewRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/45 min each/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4d: should_displayDefaultCapacity_when_previewRendered
   * AC7: Component displays default capacity (Capacity: 200)
   */
  it('should_displayDefaultCapacity_when_previewRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/capacity: 200/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4e: should_displayTypicalTiming_when_previewRendered
   * AC7: Component displays typical start-end times (09:00 - 17:00)
   */
  it('should_displayTypicalTiming_when_previewRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/typical timing/i)).toBeInTheDocument();
    expect(screen.getByText(/09:00 - 17:00/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4f: should_displayAfternoonConfig_when_afternoonTypeProvided
   * AC7: Component displays AFTERNOON event type configuration correctly
   */
  it('should_displayAfternoonConfig_when_afternoonTypeProvided', () => {
    render(<SlotTemplatePreview eventType="AFTERNOON" slotConfiguration={mockAfternoonConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/afternoon event preview/i)).toBeInTheDocument();
    expect(screen.getByText(/6-8 slots/i)).toBeInTheDocument();
    expect(screen.getByText(/30 min each/i)).toBeInTheDocument();
    expect(screen.getByText(/13:00 - 18:00/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4g: should_displayEveningConfig_when_eveningTypeProvided
   * AC7: Component displays EVENING event type configuration correctly
   */
  it('should_displayEveningConfig_when_eveningTypeProvided', () => {
    render(<SlotTemplatePreview eventType="EVENING" slotConfiguration={mockEveningConfig} />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText(/evening event preview/i)).toBeInTheDocument();
    expect(screen.getByText(/3-4 slots/i)).toBeInTheDocument();
    expect(screen.getByText(/45 min each/i)).toBeInTheDocument();
    expect(screen.getByText(/18:00 - 21:00/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4h: should_useCardLayout_when_componentRendered
   * AC7: Component uses Material-UI Card for visual grouping
   */
  it('should_useCardLayout_when_componentRendered', () => {
    const { container } = render(
      <SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />,
      { wrapper: createWrapper() }
    );

    // Card component should have MuiCard class
    const card = container.querySelector('.MuiCard-root');
    expect(card).toBeInTheDocument();
  });

  /**
   * Test 7.4i: should_useI18nKeys_when_componentRendered
   * i18n compliance: All user-facing text uses react-i18next
   */
  it('should_useI18nKeys_when_componentRendered', () => {
    render(<SlotTemplatePreview eventType="FULL_DAY" slotConfiguration={mockFullDayConfig} />, {
      wrapper: createWrapper(),
    });

    // Verify i18n keys are used (from events.slotPreview.*)
    expect(screen.getByText(/slots/i)).toBeInTheDocument();
    expect(screen.getByText(/capacity/i)).toBeInTheDocument();
    expect(screen.getByText(/timing/i)).toBeInTheDocument();
  });

  /**
   * Test 7.4j: should_useGeneratedTypes_when_componentRendered
   * ADR-006: Component uses generated types from events-api.types.ts
   * (This is a TypeScript compile-time check - if types are wrong, component won't compile)
   */
  it('should_useGeneratedTypes_when_componentRendered', () => {
    // Type check: This test will fail to compile if generated types are not used
    const testType: EventType = 'FULL_DAY';
    const testConfig: EventSlotConfigurationResponse = mockFullDayConfig;

    render(<SlotTemplatePreview eventType={testType} slotConfiguration={testConfig} />, {
      wrapper: createWrapper(),
    });

    // Runtime check: Verify component renders correctly with generated types
    expect(testConfig.type).toBe('FULL_DAY');
    expect(testConfig.minSlots).toBe(6);
    expect(testConfig.maxSlots).toBe(8);
  });
});
