/**
 * CompanyAutocomplete Component (Story 4.1.5 — reworked into a selection-locked combobox)
 *
 * A controlled company picker that decouples the three things the old free-text
 * field conflated — the search query, the human display label, and the stored
 * company id (ADR-003 slug):
 *
 *  - While searching, the input is a SEARCH BOX only. Typing never becomes the
 *    submitted value; results show each company's display name + logo + industry.
 *  - Once a company is chosen it renders as a LOCKED CHIP (display name + clear ✕).
 *    There is nothing editable, so the "edit the slug → format error" trap is gone.
 *  - When no existing company matches, an explicit "Create …" row lets the user
 *    deliberately create a new company instead of silently producing a duplicate.
 *
 * Contract: fully controlled. `value` is the stored identifier (the company slug
 * for the profile; the display name for anonymous registration), `valueLabel` is
 * the human label to show in the chip. Selection changes are reported via
 * `onCompanySelect(selection | null)`; the parent owns materialisation of a
 * brand-new company (slug === '' until it calls the get-or-create endpoint).
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Check, Plus, X, Building2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/public/ui/input';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from '@/components/public/ui/command';
import { Popover, PopoverContent, PopoverAnchor } from '@/components/public/ui/popover';
import { searchCompanies } from '@/services/api/companyApi';
import type { components } from '@/types/generated/company-api.types';
import { Loader2 } from 'lucide-react';

type Company = components['schemas']['CompanyResponse'];

/** A resolved company selection. `name` is the ADR-003 slug; '' means a brand-new,
 * not-yet-materialised company that the parent will create from `displayName`. */
export interface SelectedCompany {
  name: string;
  displayName: string;
}

interface CompanyAutocompleteProps {
  /** Stored identifier of the current selection (slug for profile, display name for registration). */
  value: string;
  /** Human label for the current selection, shown in the chip. Defaults to `value`. */
  valueLabel?: string;
  /** Reports a selection (existing or new) or `null` when the selection is cleared. */
  onCompanySelect: (selection: SelectedCompany | null) => void;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
  /** Show the explicit "Create …" row when no existing company matches (default true). */
  allowCreate?: boolean;
}

// Debounce hook
function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}

export const CompanyAutocomplete: React.FC<CompanyAutocompleteProps> = ({
  value,
  valueLabel,
  onCompanySelect,
  error,
  disabled = false,
  placeholder = 'TechCorp AG',
  allowCreate = true,
}) => {
  const { t } = useTranslation(['registration', 'common']);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  // Fully controlled: a selection exists whenever the parent holds a value or label.
  const selected: SelectedCompany | null =
    value || valueLabel ? { name: value, displayName: valueLabel || value } : null;

  const debouncedQuery = useDebounce(query, 300);
  const shouldSearch = debouncedQuery.trim().length >= 2;

  const {
    data: companies = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['companies', 'search', debouncedQuery],
    queryFn: () => searchCompanies(debouncedQuery, 10),
    enabled: shouldSearch && open && !selected,
  });

  // An exact (case-insensitive) display-name/slug match means the company already
  // exists — suppress the "Create" row so the user picks it instead of duplicating.
  const trimmedQuery = query.trim();
  const hasExactMatch = companies.some(
    (c) =>
      (c.displayName || c.name).toLowerCase() === trimmedQuery.toLowerCase() ||
      c.name.toLowerCase() === trimmedQuery.toLowerCase()
  );
  const showCreate = allowCreate && trimmedQuery.length >= 2 && !isLoading && !hasExactMatch;

  const handleSelectCompany = useCallback(
    (company: Company) => {
      onCompanySelect({ name: company.name, displayName: company.displayName || company.name });
      setQuery('');
      setOpen(false);
    },
    [onCompanySelect]
  );

  const handleCreate = useCallback(() => {
    const displayName = query.trim();
    if (!displayName) {
      return;
    }
    // name === '' signals "new, not yet materialised" — the parent resolves the
    // canonical slug (profile via get-or-create; registration server-side on submit).
    onCompanySelect({ name: '', displayName });
    setQuery('');
    setOpen(false);
  }, [query, onCompanySelect]);

  const handleClear = useCallback(() => {
    onCompanySelect(null);
    setQuery('');
    setOpen(false);
    // Return focus to the freshly-revealed search box.
    setTimeout(() => inputRef.current?.focus(), 0);
  }, [onCompanySelect]);

  const handleQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setQuery(e.target.value);
    setOpen(true);
  }, []);

  const handleInputFocus = useCallback(() => {
    if (query.trim().length >= 2) {
      setOpen(true);
    }
  }, [query]);

  const handleInputBlur = useCallback(() => {
    // Delay so a click on a suggestion registers before the popover closes.
    setTimeout(() => setOpen(false), 200);
  }, []);

  // ── Selected: locked chip ───────────────────────────────────────────────
  if (selected) {
    return (
      <div className="relative">
        <div
          data-testid="registration-company-chip"
          className={cn(
            'flex items-center gap-2 rounded-md border bg-zinc-900 px-3 py-2 min-h-[44px]',
            error ? 'border-red-500' : 'border-zinc-800'
          )}
        >
          <Building2 className="h-4 w-4 shrink-0 text-zinc-500" />
          <span className="flex-1 truncate text-zinc-100">{selected.displayName}</span>
          {!disabled && (
            <button
              type="button"
              data-testid="registration-company-clear"
              onClick={handleClear}
              aria-label={t('companySearch.clear')}
              className="shrink-0 rounded p-1 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
      </div>
    );
  }

  // ── Unselected: search box + suggestions ────────────────────────────────
  return (
    <div className="relative">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverAnchor asChild>
          <div className="relative">
            <Input
              ref={inputRef}
              data-testid="registration-company-input"
              value={query}
              onChange={handleQueryChange}
              onFocus={handleInputFocus}
              onBlur={handleInputBlur}
              disabled={disabled}
              placeholder={placeholder}
              className={cn('bg-zinc-900 border-zinc-800 text-zinc-100', error && 'border-red-500')}
              autoComplete="off"
            />
            {isLoading && (
              <div className="absolute right-3 top-1/2 -translate-y-1/2">
                <Loader2 className="h-4 w-4 animate-spin text-zinc-400" />
              </div>
            )}
          </div>
        </PopoverAnchor>

        <PopoverContent
          className="w-[var(--radix-popover-trigger-width)] p-0 bg-zinc-900 border-zinc-800"
          align="start"
          onOpenAutoFocus={(e: Event) => e.preventDefault()}
        >
          <Command className="bg-zinc-900" shouldFilter={false}>
            <CommandList>
              {isLoading && (
                <div className="py-6 text-center text-sm text-zinc-500 flex items-center justify-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('common:actions.loading')}
                </div>
              )}

              {!isLoading && shouldSearch && companies.length === 0 && !showCreate && (
                <CommandEmpty className="text-zinc-500 py-6 text-center text-sm">
                  {isError ? t('companySearch.error') : t('companySearch.noResults')}
                </CommandEmpty>
              )}

              {!isLoading && !shouldSearch && query.length > 0 && (
                <div className="py-6 text-center text-sm text-zinc-500">
                  {t('companySearch.minChars')}
                </div>
              )}

              {!isLoading && companies.length > 0 && (
                <CommandGroup>
                  {companies.map((company) => (
                    <CommandItem
                      key={company.name}
                      value={company.name}
                      onSelect={() => handleSelectCompany(company)}
                      className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-zinc-800 aria-selected:bg-zinc-800"
                    >
                      {/* Company Logo */}
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-zinc-800">
                        {company.logo?.url ? (
                          <img
                            src={company.logo.url}
                            alt={company.displayName || company.name}
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          <span className="text-sm font-medium text-zinc-400">
                            {(company.displayName || company.name).charAt(0).toUpperCase()}
                          </span>
                        )}
                      </div>

                      {/* Company Info — display name, never the slug */}
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-zinc-100 truncate">
                          {company.displayName || company.name}
                        </div>
                        {company.industry && (
                          <div className="text-xs text-zinc-500 truncate">{company.industry}</div>
                        )}
                      </div>

                      <Check
                        className={cn(
                          'ml-auto h-4 w-4',
                          value === company.name ? 'opacity-100' : 'opacity-0'
                        )}
                      />
                    </CommandItem>
                  ))}
                </CommandGroup>
              )}

              {/* Explicit, deliberate "create new company" affordance */}
              {showCreate && (
                <CommandGroup>
                  <CommandItem
                    key="__create__"
                    value={`__create__${trimmedQuery}`}
                    data-testid="registration-company-create-option"
                    onSelect={handleCreate}
                    className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-zinc-800 aria-selected:bg-zinc-800"
                  >
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-800">
                      <Plus className="h-4 w-4 text-zinc-300" />
                    </div>
                    <span className="text-sm text-zinc-100 truncate">
                      {t('companySearch.createOption', { value: trimmedQuery })}
                    </span>
                  </CommandItem>
                </CommandGroup>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}

      {!error && query.length > 0 && query.length < 2 && (
        <p className="text-xs text-zinc-500 mt-1">{t('companySearch.minChars')}</p>
      )}
    </div>
  );
};
