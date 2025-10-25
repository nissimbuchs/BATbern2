/**
 * CodeInput Component Tests (TDD)
 * Story 1.2.4: Email Verification Flow - Task 5 & 6
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeInput } from './CodeInput';

describe('CodeInput Component', () => {
  // Test 1.1: should_render6InputBoxes_when_componentMounted
  it('should_render6InputBoxes_when_componentMounted', () => {
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    // Check for 6 input boxes
    for (let i = 1; i <= 6; i++) {
      expect(screen.getByLabelText(`Digit ${i}`)).toBeInTheDocument();
    }
  });

  // Test 1.2: should_autoAdvanceFocus_when_digitEntered
  it('should_autoAdvanceFocus_when_digitEntered', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;
    const input2 = screen.getByLabelText('Digit 2') as HTMLInputElement;

    await user.type(input1, '1');

    // Focus should move to second input
    expect(document.activeElement).toBe(input2);
  });

  // Test 1.3: should_distributePastedCode_when_6DigitsPasted
  it('should_distributePastedCode_when_6DigitsPasted', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    // Paste full code
    await user.click(input1);
    await user.paste('123456');

    // Check all inputs have the correct digits
    expect((screen.getByLabelText('Digit 1') as HTMLInputElement).value).toBe('1');
    expect((screen.getByLabelText('Digit 2') as HTMLInputElement).value).toBe('2');
    expect((screen.getByLabelText('Digit 3') as HTMLInputElement).value).toBe('3');
    expect((screen.getByLabelText('Digit 4') as HTMLInputElement).value).toBe('4');
    expect((screen.getByLabelText('Digit 5') as HTMLInputElement).value).toBe('5');
    expect((screen.getByLabelText('Digit 6') as HTMLInputElement).value).toBe('6');
  });

  // Test 1.4: should_moveToPreviousBox_when_backspacePressed
  it('should_moveToPreviousBox_when_backspacePressed', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;
    const input2 = screen.getByLabelText('Digit 2') as HTMLInputElement;

    // Enter a digit in first box
    await user.type(input1, '1');

    // Now in second box, press backspace
    await user.type(input2, '{Backspace}');

    // Focus should move back to first input
    expect(document.activeElement).toBe(input1);
  });

  // Test 1.5: should_autoSubmit_when_allDigitsEntered
  it('should_autoSubmit_when_allDigitsEntered', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    // Enter all 6 digits one by one
    await user.type(input1, '1');
    await user.type(screen.getByLabelText('Digit 2'), '2');
    await user.type(screen.getByLabelText('Digit 3'), '3');
    await user.type(screen.getByLabelText('Digit 4'), '4');
    await user.type(screen.getByLabelText('Digit 5'), '5');
    await user.type(screen.getByLabelText('Digit 6'), '6');

    // onComplete should be called with full code
    expect(onComplete).toHaveBeenCalledWith('123456');
  });

  // Test 1.6: should_callOnCodeChange_when_digitEntered
  it('should_callOnCodeChange_when_digitEntered', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    const onCodeChange = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} onCodeChange={onCodeChange} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    await user.type(input1, '1');

    // onCodeChange should be called with partial code
    expect(onCodeChange).toHaveBeenCalledWith('1');
  });

  // Test 1.7: should_onlyAcceptDigits_when_nonNumericEntered
  it('should_onlyAcceptDigits_when_nonNumericEntered', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    // Try to enter non-numeric characters
    await user.type(input1, 'abc');

    // Input should remain empty
    expect(input1.value).toBe('');
  });

  // Test 1.8: should_displayError_when_errorPropTrue
  it('should_displayError_when_errorPropTrue', () => {
    const onComplete = vi.fn();
    const { container } = render(<CodeInput length={6} onComplete={onComplete} error={true} />);

    // Check for error styling (MUI adds error class)
    const errorInputs = container.querySelectorAll('.MuiInputBase-root.Mui-error');
    expect(errorInputs.length).toBe(6);
  });

  // Test 1.9: should_autoSubmitOnPaste_when_6DigitsPasted
  it('should_autoSubmitOnPaste_when_6DigitsPasted', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    // Paste full code
    await user.click(input1);
    await user.paste('123456');

    // onComplete should be called immediately
    expect(onComplete).toHaveBeenCalledWith('123456');
  });

  // Test 1.10: should_notAcceptNonNumericPaste_when_pastedWithLetters
  it('should_notAcceptNonNumericPaste_when_pastedWithLetters', async () => {
    const user = userEvent.setup();
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} />);

    const input1 = screen.getByLabelText('Digit 1') as HTMLInputElement;

    // Try to paste non-numeric code
    await user.click(input1);
    await user.paste('abc123');

    // All inputs should remain empty
    expect((screen.getByLabelText('Digit 1') as HTMLInputElement).value).toBe('');
  });

  // Test 1.11: should_disableInputs_when_disabledPropTrue
  it('should_disableInputs_when_disabledPropTrue', () => {
    const onComplete = vi.fn();
    render(<CodeInput length={6} onComplete={onComplete} disabled={true} />);

    // All inputs should be disabled
    for (let i = 1; i <= 6; i++) {
      expect(screen.getByLabelText(`Digit ${i}`)).toBeDisabled();
    }
  });
});
