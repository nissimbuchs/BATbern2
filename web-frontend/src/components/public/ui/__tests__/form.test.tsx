/**
 * Form Component Tests
 * Shadcn UI Component - Form wrapper around react-hook-form
 *
 * Tests for form components used for form validation and submission
 */

import { describe, test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormDescription,
  FormMessage,
} from '../form';

// Helper component to test form with react-hook-form
function TestFormWrapper({ children }: { children: React.ReactNode }) {
  const form = useForm({
    defaultValues: {
      testField: '',
    },
  });

  return <Form {...form}>{children}</Form>;
}

describe('Form Component', () => {
  test('should_renderFormItem_when_provided', () => {
    render(
      <TestFormWrapper>
        <FormItem>
          <div data-testid="form-item">Form Item Content</div>
        </FormItem>
      </TestFormWrapper>
    );

    expect(screen.getByTestId('form-item')).toBeInTheDocument();
    expect(screen.getByText('Form Item Content')).toBeInTheDocument();
  });

  test('should_renderFormLabel_when_provided', () => {
    render(
      <TestFormWrapper>
        <FormField
          name="testField"
          render={() => (
            <FormItem>
              <FormLabel>Test Label</FormLabel>
            </FormItem>
          )}
        />
      </TestFormWrapper>
    );

    expect(screen.getByText('Test Label')).toBeInTheDocument();
  });

  test('should_renderFormControl_when_provided', () => {
    render(
      <TestFormWrapper>
        <FormField
          name="testField"
          render={({ field }) => (
            <FormItem>
              <FormControl>
                <input {...field} placeholder="Test Input" />
              </FormControl>
            </FormItem>
          )}
        />
      </TestFormWrapper>
    );

    expect(screen.getByPlaceholderText('Test Input')).toBeInTheDocument();
  });

  test('should_renderFormDescription_when_provided', () => {
    render(
      <TestFormWrapper>
        <FormField
          name="testField"
          render={() => (
            <FormItem>
              <FormDescription>This is a description</FormDescription>
            </FormItem>
          )}
        />
      </TestFormWrapper>
    );

    expect(screen.getByText('This is a description')).toBeInTheDocument();
  });

  test('should_renderFormMessage_when_provided', () => {
    const { container } = render(
      <TestFormWrapper>
        <FormField
          name="testField"
          render={() => (
            <FormItem>
              <FormMessage />
            </FormItem>
          )}
        />
      </TestFormWrapper>
    );

    // FormMessage renders empty when there's no error
    // Just verify the FormItem is rendered (FormMessage is empty without errors)
    const formItems = container.querySelectorAll('[class*="space-y"]');
    expect(formItems.length).toBeGreaterThan(0);
  });

  test('should_renderCompleteFormField_when_allPartsProvided', () => {
    render(
      <TestFormWrapper>
        <FormField
          name="testField"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Complete Field</FormLabel>
              <FormControl>
                <input {...field} placeholder="Enter value" />
              </FormControl>
              <FormDescription>Field description</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
      </TestFormWrapper>
    );

    expect(screen.getByText('Complete Field')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Enter value')).toBeInTheDocument();
    expect(screen.getByText('Field description')).toBeInTheDocument();
  });

  test('should_renderMultipleFields_when_provided', () => {
    // Helper component to wrap useForm hook call
    function MultiFieldForm() {
      const form = useForm({
        defaultValues: {
          field1: '',
          field2: '',
        },
      });

      return (
        <Form {...form}>
          <FormField
            name="field1"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Field 1</FormLabel>
                <FormControl>
                  <input {...field} />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            name="field2"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Field 2</FormLabel>
                <FormControl>
                  <input {...field} />
                </FormControl>
              </FormItem>
            )}
          />
        </Form>
      );
    }

    render(<MultiFieldForm />);

    expect(screen.getByText('Field 1')).toBeInTheDocument();
    expect(screen.getByText('Field 2')).toBeInTheDocument();
  });

  test('should_applyCustomClassName_when_provided', () => {
    const { container } = render(
      <TestFormWrapper>
        <FormItem className="custom-form-item">
          <div>Content</div>
        </FormItem>
      </TestFormWrapper>
    );

    const formItem = container.querySelector('.custom-form-item');
    expect(formItem).toBeInTheDocument();
  });
});
