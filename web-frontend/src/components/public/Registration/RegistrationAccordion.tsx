/**
 * RegistrationAccordion Component (Story 4.1.5 - Task 4a)
 *
 * Accordion wrapper using shadcn Collapsible for smooth step transitions.
 * Used in registration wizard for step-by-step form flow.
 */

import { ReactNode } from 'react';
import { Collapsible, CollapsibleContent } from '@/components/public/ui/ui/collapsible';

export interface RegistrationAccordionProps {
  /** Whether the accordion section is expanded */
  isExpanded: boolean;
  /** Section title */
  title: string;
  /** Content to display when expanded */
  children: ReactNode;
  /** Optional summary content shown when collapsed */
  summary?: ReactNode;
  /** Optional click handler for the title */
  onToggle?: () => void;
}

/**
 * Accordion component for registration wizard steps.
 * Provides smooth expand/collapse animations using shadcn Collapsible.
 */
export const RegistrationAccordion = ({
  isExpanded,
  title,
  children,
  summary,
  onToggle,
}: RegistrationAccordionProps) => {
  const handleHeaderClick = () => {
    // Only allow toggle when collapsed
    if (!isExpanded && onToggle) {
      onToggle();
    }
  };

  return (
    <Collapsible
      open={isExpanded}
      className="border border-zinc-800 rounded-lg overflow-hidden bg-zinc-900/50"
    >
      <div
        className={`p-4 transition-colors ${
          isExpanded ? 'bg-zinc-800/50' : 'cursor-pointer hover:bg-zinc-800/30'
        }`}
        onClick={handleHeaderClick}
        role="button"
        tabIndex={isExpanded ? -1 : 0}
      >
        <h3 className="text-lg font-light text-zinc-100">{title}</h3>
      </div>

      {/* Collapsed state summary */}
      {!isExpanded && summary && (
        <div className="p-4 border-t border-zinc-800">
          <div className="text-sm text-zinc-400">{summary}</div>
        </div>
      )}

      {/* Expanded state content */}
      <CollapsibleContent className="transition-all duration-300 data-[state=closed]:animate-accordion-up data-[state=open]:animate-accordion-down">
        <div className="p-6 border-t border-zinc-800">{children}</div>
      </CollapsibleContent>
    </Collapsible>
  );
};
