/**
 * CompanyAutocomplete Component (Story 4.1.5 - Enhancement)
 *
 * Public-facing autocomplete for company search using shadcn components.
 * Features:
 * - Free-form text input (allows creating new companies)
 * - Debounced search (300ms)
 * - Min 2 characters to trigger search
 * - Dark theme styling consistent with public website
 * - Loading and error states
 * - Company logo and industry display
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/public/ui/ui/input';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from '@/components/public/ui/ui/command';
import { Popover, PopoverContent, PopoverAnchor } from '@/components/public/ui/ui/popover';
import { searchCompanies } from '@/services/api/companyApi';
import type { components } from '@/types/generated/company-api.types';
import { Loader2 } from 'lucide-react';

type Company = components['schemas']['CompanyResponse'];

interface CompanyAutocompleteProps {
  value: string;
  onCompanySelect: (companyName: string) => void;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
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
  onCompanySelect,
  error,
  disabled = false,
  placeholder = 'TechCorp AG',
}) => {
  const [open, setOpen] = useState(false);
  const [inputValue, setInputValue] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  const debouncedInputValue = useDebounce(inputValue, 300);

  // Sync inputValue when value prop changes externally
  useEffect(() => {
    setInputValue(value);
  }, [value]);

  // Only search if input is at least 2 characters
  const shouldSearch = debouncedInputValue.length >= 2;

  // Query for company search
  const {
    data: companies = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['companies', 'search', debouncedInputValue],
    queryFn: () => searchCompanies(debouncedInputValue, 10),
    enabled: shouldSearch && open,
  });

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      setInputValue(newValue);
      onCompanySelect(newValue); // Always sync free-form text to parent
      setOpen(true); // Open suggestions when typing
    },
    [onCompanySelect]
  );

  const handleSelectCompany = useCallback(
    (company: Company) => {
      const companyName = company.name;
      setInputValue(companyName);
      onCompanySelect(companyName);
      setOpen(false);
    },
    [onCompanySelect]
  );

  const handleInputFocus = useCallback(() => {
    if (inputValue.length >= 2) {
      setOpen(true);
    }
  }, [inputValue]);

  const handleInputBlur = useCallback(() => {
    // Delay to allow click on suggestion
    setTimeout(() => setOpen(false), 200);
  }, []);

  return (
    <div className="relative">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverAnchor asChild>
          <div className="relative">
            <Input
              ref={inputRef}
              value={inputValue}
              onChange={handleInputChange}
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
          <Command className="bg-zinc-900">
            <CommandList>
              {!isLoading && shouldSearch && companies.length === 0 && (
                <CommandEmpty className="text-zinc-500 py-6 text-center text-sm">
                  {isError ? (
                    'Error loading companies'
                  ) : (
                    <div className="space-y-1">
                      <div>No existing company found</div>
                      <div className="text-xs text-zinc-600">
                        You can continue with "{inputValue}" - we'll create it for you
                      </div>
                    </div>
                  )}
                </CommandEmpty>
              )}

              {!isLoading && !shouldSearch && inputValue.length > 0 && (
                <div className="py-6 text-center text-sm text-zinc-500">
                  Type at least 2 characters to search
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

                      {/* Company Info */}
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-zinc-100 truncate">
                          {company.displayName || company.name}
                        </div>
                        {company.industry && (
                          <div className="text-xs text-zinc-500 truncate">{company.industry}</div>
                        )}
                      </div>

                      {/* Check icon for selected */}
                      <Check
                        className={cn(
                          'ml-auto h-4 w-4',
                          inputValue === company.name ? 'opacity-100' : 'opacity-0'
                        )}
                      />
                    </CommandItem>
                  ))}
                </CommandGroup>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>

      {/* Helper text */}
      {!error && inputValue.length > 0 && inputValue.length < 2 && (
        <p className="text-xs text-zinc-500 mt-1">Type at least 2 characters to search</p>
      )}
    </div>
  );
};
