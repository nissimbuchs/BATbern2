/**
 * CodeInput Component
 * Story 1.2.4: Email Verification Flow - Task 6 (GREEN Phase)
 * 6-digit code input with auto-advance and paste support
 */

import React, { useRef, useState, KeyboardEvent, ClipboardEvent, ChangeEvent } from 'react';
import { Box, TextField } from '@mui/material';

interface CodeInputProps {
  length: number;
  onComplete: (code: string) => void;
  onCodeChange?: (code: string) => void;
  error?: boolean;
  disabled?: boolean;
}

export const CodeInput: React.FC<CodeInputProps> = ({
  length,
  onComplete,
  onCodeChange,
  error = false,
  disabled = false,
}) => {
  const [code, setCode] = useState<string[]>(Array(length).fill(''));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  const handleChange = (index: number, value: string) => {
    if (disabled) return;

    // Only allow digits
    if (!/^\d*$/.test(value)) return;

    const newCode = [...code];
    newCode[index] = value.slice(-1); // Only last digit
    setCode(newCode);

    const codeString = newCode.join('');
    onCodeChange?.(codeString);

    // Auto-advance to next box
    if (value && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    // Auto-submit when complete (check if all digits are filled)
    const isComplete = newCode.every((digit) => digit !== '');
    if (isComplete) {
      onComplete(codeString);
    }
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (disabled) return;

    if (e.key === 'Backspace' && !code[index] && index > 0) {
      // Move to previous box on backspace
      inputRefs.current[index - 1]?.focus();
      const newCode = [...code];
      newCode[index - 1] = '';
      setCode(newCode);
      onCodeChange?.(newCode.join(''));
    }
  };

  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    if (disabled) return;

    e.preventDefault();
    const pastedData = e.clipboardData.getData('text').slice(0, length);

    // Only allow digits
    if (!/^\d+$/.test(pastedData)) return;

    const newCode = pastedData.split('').concat(Array(length).fill('')).slice(0, length);
    setCode(newCode);

    const codeString = newCode.join('');
    onCodeChange?.(codeString);

    // Focus last filled box or last box
    const lastFilledIndex = Math.min(pastedData.length - 1, length - 1);
    inputRefs.current[lastFilledIndex]?.focus();

    // Auto-submit if complete
    if (pastedData.length === length) {
      onComplete(codeString);
    }
  };

  // Clear all inputs (exposed for error handling)
  React.useImperativeHandle(React.useRef<{ clearInputs: () => void }>(), () => ({
    clearInputs: () => {
      setCode(Array(length).fill(''));
      onCodeChange?.('');
      inputRefs.current[0]?.focus();
    },
  }));

  return (
    <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
      {code.map((digit, index) => (
        <TextField
          key={index}
          inputRef={(el) => (inputRefs.current[index] = el)}
          value={digit}
          onChange={(e: ChangeEvent<HTMLInputElement>) => handleChange(index, e.target.value)}
          onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          disabled={disabled}
          inputProps={{
            maxLength: 1,
            style: { textAlign: 'center', fontSize: '24px' },
            inputMode: 'numeric' as const,
            'aria-label': `Digit ${index + 1}`,
          }}
          sx={{
            width: 56,
            '& input': {
              padding: '16px 0',
            },
          }}
          error={error}
          autoFocus={index === 0}
        />
      ))}
    </Box>
  );
};

export default CodeInput;
