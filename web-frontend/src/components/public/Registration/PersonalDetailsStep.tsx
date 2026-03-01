/**
 * PersonalDetailsStep Component (Story 4.1.5 - Task 5)
 *
 * Step 1 of registration wizard - collects personal information.
 * Uses react-hook-form with zod validation for robust form handling.
 */

import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useImperativeHandle, forwardRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/public/ui/form';
import { Input } from '@/components/public/ui/input';
import { CompanyAutocomplete } from './CompanyAutocomplete';
import type { CreateRegistrationRequest } from '@/types/event.types';

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
    const { t } = useTranslation('registration');

    const personalDetailsSchema = useMemo(
      () =>
        z.object({
          firstName: z.string().min(1, t('personalDetails.validation.firstNameRequired')),
          lastName: z.string().min(1, t('personalDetails.validation.lastNameRequired')),
          email: z.string().email(t('personalDetails.validation.invalidEmail')),
          company: z.string().min(1, t('personalDetails.validation.companyRequired')),
          role: z.string().min(1, t('personalDetails.validation.roleRequired')),
        }),
      [t]
    );

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
          <h2 className="text-2xl font-light text-zinc-100 mb-2">{t('personalDetails.title')}</h2>
          <p className="text-sm text-zinc-400">{t('personalDetails.subtitle')}</p>
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
                    <FormLabel className="text-zinc-300">
                      {t('personalDetails.fields.firstName')}
                    </FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder={t('personalDetails.placeholders.firstName')}
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
                    <FormLabel className="text-zinc-300">
                      {t('personalDetails.fields.lastName')}
                    </FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder={t('personalDetails.placeholders.lastName')}
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
                  <FormLabel className="text-zinc-300">
                    {t('personalDetails.fields.emailAddress')}
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      type="email"
                      className="bg-zinc-900 border-zinc-800 text-zinc-100"
                      placeholder={t('personalDetails.placeholders.email')}
                    />
                  </FormControl>
                  <p className="text-xs text-zinc-500">{t('personalDetails.fields.emailHelper')}</p>
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
                    <FormLabel className="text-zinc-300">
                      {t('personalDetails.fields.company')}
                    </FormLabel>
                    <FormControl>
                      <CompanyAutocomplete
                        value={field.value}
                        onCompanySelect={(companyName) => {
                          field.onChange(companyName);
                        }}
                        error={fieldState.error?.message}
                        placeholder={t('personalDetails.placeholders.company')}
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
                    <FormLabel className="text-zinc-300">
                      {t('personalDetails.fields.role')}
                    </FormLabel>
                    <FormControl>
                      <Input
                        {...field}
                        className="bg-zinc-900 border-zinc-800 text-zinc-100"
                        placeholder={t('personalDetails.placeholders.role')}
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
