/**
 * The functions `companyApi.ts` exports for direct use reject an HTTP error with the transformed
 * error (status, correlation id), like the client methods. They used to be destructured from the
 * client unbound, so their catch path called `this.transformError` on `undefined` and rejected
 * with a `TypeError` instead (found converting CompanyAutocomplete onto the MSW harness, 17.0c2).
 */
import { describe, expect, it } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import {
  createCompany,
  deleteCompany,
  getCompanies,
  getCompany,
  getOrCreateCompany,
  searchCompanies,
  updateCompany,
} from './companyApi';

const problem = { error: 'INTERNAL_ERROR', message: 'Company service down', status: 500 };
const down = () => HttpResponse.json(problem, { status: 500 });

describe('companyApi exported functions', () => {
  it.each([
    ['searchCompanies', 'searchCompanies', () => searchCompanies('Brünnimann')],
    ['getCompanies', 'listCompanies', () => getCompanies()],
    ['getCompany', 'getCompany', () => getCompany('brunnimann')],
    [
      'createCompany',
      'createCompany',
      () => createCompany({ name: 'brunnimann', displayName: 'Brünnimann AG' }),
    ],
    ['getOrCreateCompany', 'getOrCreateCompany', () => getOrCreateCompany('Brünnimann AG')],
    ['updateCompany', 'patchCompany', () => updateCompany('brunnimann', { industry: 'Bau' })],
    ['deleteCompany', 'deleteCompany', () => deleteCompany('brunnimann')],
  ] as const)('%s rejects a 500 with the transformed error', async (_name, op, call) => {
    api.on(op, down);

    const error = await call().then(
      () => undefined,
      (e: unknown) => e
    );

    expect(api.calls(op)).toHaveLength(1);
    expect(error).not.toBeInstanceOf(TypeError);
    expect(error).toMatchObject({ message: 'Company service down', status: 500 });
  });
});
