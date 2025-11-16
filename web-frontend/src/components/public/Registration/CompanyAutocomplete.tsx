/**
 * CompanyAutocomplete Component (Story 4.1.5 - Enhancement)
 *
 * Public-facing autocomplete for company search using shadcn/Tailwind styling.
 * Features:
 * - Debounced search (300ms)
 * - Min 2 characters to trigger search
 * - Dark theme styling consistent with public website
 * - Loading and error states
 * - Company logo and industry display
 */

import { useState, useRef, useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Input } from '@/components/public/ui/ui/input';
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
  const [inputValue, setInputValue] = useState(value);
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const debouncedInputValue = useDebounce(inputValue, 300);

  // Sync inputValue when value prop changes
  useEffect(() => {
    setInputValue(value);
  }, [value]);

  // Only search if input is at least 2 characters and different from value
  const shouldSearch = debouncedInputValue.length >= 2 && debouncedInputValue !== value;

  // Query for company search
  const {
    data: companies = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['companies', 'search', debouncedInputValue],
    queryFn: () => searchCompanies(debouncedInputValue, 10),
    enabled: shouldSearch,
  });

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setSelectedIndex(-1);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Open dropdown when there are results
  useEffect(() => {
    if (companies.length > 0 && shouldSearch) {
      setIsOpen(true);
    }
  }, [companies, shouldSearch]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      setInputValue(newValue);
      onCompanySelect(newValue); // Always sync text input to form
      setSelectedIndex(-1);
    },
    [onCompanySelect]
  );

  const handleSelectCompany = useCallback(
    (company: Company) => {
      const companyName = company.name;
      setInputValue(companyName);
      onCompanySelect(companyName);
      setIsOpen(false);
      setSelectedIndex(-1);
      inputRef.current?.blur();
    },
    [onCompanySelect]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!isOpen || companies.length === 0) return;

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((prev) => (prev < companies.length - 1 ? prev + 1 : prev));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((prev) => (prev > 0 ? prev - 1 : -1));
          break;
        case 'Enter':
          e.preventDefault();
          if (selectedIndex >= 0 && selectedIndex < companies.length) {
            handleSelectCompany(companies[selectedIndex]);
          }
          break;
        case 'Escape':
          setIsOpen(false);
          setSelectedIndex(-1);
          break;
      }
    },
    [isOpen, companies, selectedIndex, handleSelectCompany]
  );

  // Scroll selected item into view
  useEffect(() => {
    if (selectedIndex >= 0 && listRef.current) {
      const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
      if (selectedElement) {
        selectedElement.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [selectedIndex]);

  return (
    <div ref={wrapperRef} className="relative">
      <div className="relative">
        <Input
          ref={inputRef}
          value={inputValue}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            if (companies.length > 0 && shouldSearch) {
              setIsOpen(true);
            }
          }}
          disabled={disabled}
          placeholder={placeholder}
          className={`bg-zinc-900 border-zinc-800 text-zinc-100 ${error ? 'border-red-500' : ''}`}
          autoComplete="off"
        />
        {isLoading && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            <Loader2 className="h-4 w-4 animate-spin text-zinc-400" />
          </div>
        )}
      </div>

      {/* Helper text */}
      {!error && inputValue.length > 0 && inputValue.length < 2 && (
        <p className="text-xs text-zinc-500 mt-1">Type at least 2 characters to search</p>
      )}

      {/* Dropdown */}
      {isOpen && companies.length > 0 && (
        <ul
          ref={listRef}
          className="absolute z-50 mt-1 w-full max-h-60 overflow-auto rounded-md border border-zinc-800 bg-zinc-900 py-1 shadow-lg"
        >
          {companies.map((company, index) => (
            <li
              key={company.name}
              onClick={() => handleSelectCompany(company)}
              onMouseEnter={() => setSelectedIndex(index)}
              className={`flex cursor-pointer items-center gap-3 px-3 py-2 ${
                index === selectedIndex ? 'bg-zinc-800' : 'hover:bg-zinc-800/50'
              }`}
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
            </li>
          ))}
        </ul>
      )}

      {/* No results message */}
      {isOpen && shouldSearch && !isLoading && companies.length === 0 && (
        <div className="absolute z-50 mt-1 w-full rounded-md border border-zinc-800 bg-zinc-900 px-3 py-2 text-sm text-zinc-500">
          {isError ? 'Error loading companies' : 'No companies found'}
        </div>
      )}
    </div>
  );
};
