/**
 * PersonalDetailsStep Component (Story 4.1.5 - Task 5)
 *
 * Step 1 of registration wizard - collects personal information.
 * Uses react-hook-form with zod validation for robust form handling.
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useImperativeHandle, forwardRef } from 'react';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/public/ui/form';
import { Input } from '@/components/public/ui/ui/input';
import { CompanyAutocomplete } from './CompanyAutocomplete';
import type { CreateRegistrationRequest } from '@/types/event.types';

// Validation schema for personal details
const personalDetailsSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().email('Invalid email address'),
  company: z.string().min(1, 'Company is required'),
  role: z.string().min(1, 'Role is required'),
});

export interface PersonalDetailsStepProps {
  /** Current form data */
  formData: CreateRegistrationRequest;
  /** Form data update handler */
  setFormData: (
    data:
      | CreateRegistrationRequest
      | ((prev: CreateRegistrationRequest) => CreateRegistrationRequest)
  ) => void;
}

export interface PersonalDetailsStepRef {
  validateAndSync: () => Promise<boolean>;
}

/**
 * Step 1: Personal Details Form
 *
 * Collects firstName, lastName, email, company, and role.
 * Form is self-contained and only syncs to parent when validated.
 */
export const PersonalDetailsStep = forwardRef<PersonalDetailsStepRef, PersonalDetailsStepProps>(
  ({ formData, setFormData }, ref) => {
    const form = useForm({
      resolver: zodResolver(personalDetailsSchema),
      mode: 'onBlur', // Only validate on blur, not on change
      defaultValues: {
        firstName: formData.firstName || '',
        lastName: formData.lastName || '',
        email: formData.email || '',
        company: formData.company || '',
        role: formData.role || '',
      },
    });

    // Expose validation function to parent
    useImperativeHandle(ref, () => ({
      validateAndSync: async () => {
        const isValid = await form.trigger();
        if (isValid) {
          const values = form.getValues();
          setFormData((prev) => ({ ...prev, ...values }));
        }
        return isValid;
      },
    }));

    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-2xl font-light text-zinc-100 mb-2">Personal Information</h2>
          <p className="text-sm text-zinc-400">
            Please provide your details to register for the event.
          </p>
        </div>

        <Form {...form}>
          <div className="space-y-4">
            {/* First Name and Last Name - Side by Side */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-zinc-300">First Name *</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder="John"
                      />
                    </FormControl>
                    <FormMessage className="text-red-400" />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-zinc-300">Last Name *</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder="Smith"
                      />
                    </FormControl>
                    <FormMessage className="text-red-400" />
                  </FormItem>
                )}
              />
            </div>

            {/* Email */}
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-zinc-300">Email Address *</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      type="email"
                      className="bg-zinc-900 border-zinc-800 text-zinc-100"
                      placeholder="john.smith@company.ch"
                    />
                  </FormControl>
                  <p className="text-xs text-zinc-500">
                    We'll send your ticket and event updates here
                  </p>
                  <FormMessage className="text-red-400" />
                </FormItem>
              )}
            />

            {/* Company and Role - Side by Side */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="company"
                render={({ field, fieldState }) => (
                  <FormItem>
                    <FormLabel className="text-zinc-300">Company *</FormLabel>
                    <FormControl>
                      <CompanyAutocomplete
                        value={field.value}
                        onCompanySelect={(companyName) => {
                          field.onChange(companyName);
                        }}
                        error={fieldState.error?.message}
                        placeholder="Search for your company..."
                      />
                    </FormControl>
                    <FormMessage className="text-red-400" />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="role"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel className="text-zinc-300">Role *</FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder="Senior Developer"
                      />
                    </FormControl>
                    <FormMessage className="text-red-400" />
                  </FormItem>
                )}
              />
            </div>
          </div>
        </Form>
      </div>
    );
  }
);

PersonalDetailsStep.displayName = 'PersonalDetailsStep';
