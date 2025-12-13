/**
 * Unit tests for Company Import Utilities
 */

import { describe, it, expect } from 'vitest';
import {
  transformIdToApiName,
  isValidUrl,
  getLogoUrl,
  transformCompanyForApi,
  parseCompanyJson,
  createImportCandidates,
  isDuplicateName,
} from './companyImport';
import type { SourceCompany } from '@/types/companyImport.types';

describe('transformIdToApiName', () => {
  it('should_removeHyphens_when_idContainsHyphens', () => {
    expect(transformIdToApiName('isc-ejpd')).toBe('iscejpd');
  });

  it('should_removeSpecialCharacters_when_idContainsUmlauts', () => {
    expect(transformIdToApiName('zühlke')).toBe('zhlke');
  });

  it('should_convertToLowercase_when_idContainsMixedCase', () => {
    expect(transformIdToApiName('GoogleCloud')).toBe('googlecloud');
  });

  it('should_keepAlphanumeric_when_idIsSimple', () => {
    expect(transformIdToApiName('sbb')).toBe('sbb');
    expect(transformIdToApiName('aws')).toBe('aws');
  });

  it('should_handleMultipleHyphens_when_idHasMultiple', () => {
    expect(transformIdToApiName('some-company-name')).toBe('somecompanyname');
  });

  it('should_removeUnderscores_when_idContainsThem', () => {
    expect(transformIdToApiName('company_name')).toBe('companyname');
  });
});

describe('isValidUrl', () => {
  it('should_returnTrue_when_urlIsValid', () => {
    expect(isValidUrl('https://www.example.com')).toBe(true);
    expect(isValidUrl('http://example.com')).toBe(true);
    expect(isValidUrl('https://subdomain.example.com/path')).toBe(true);
  });

  it('should_returnFalse_when_urlIsNull', () => {
    expect(isValidUrl(null)).toBe(false);
  });

  it('should_returnFalse_when_urlIsUndefined', () => {
    expect(isValidUrl(undefined)).toBe(false);
  });

  it('should_returnFalse_when_urlIsInvalid', () => {
    expect(isValidUrl('not-a-url')).toBe(false);
    expect(isValidUrl('')).toBe(false);
    expect(isValidUrl('example.com')).toBe(false);
  });
});

describe('getLogoUrl', () => {
  it('should_returnLogoUrl_when_logoUrlExists', () => {
    const company: SourceCompany = {
      id: 'puzzle',
      displayName: 'Puzzle ITC',
      logo: null,
      url: 'https://www.puzzle.ch',
      speakerCount: 3,
      logoFilePath: null,
      status: 'complete',
      logoUrl: 'https://www.puzzle.ch/logo.svg',
    };
    expect(getLogoUrl(company)).toBe('https://www.puzzle.ch/logo.svg');
  });

  it('should_returnNull_when_onlyLocalFilePathExists', () => {
    const company: SourceCompany = {
      id: 'sbb',
      displayName: 'SBB CFF FFS',
      logo: 'sbb.jpg',
      url: 'https://www.sbb.ch',
      speakerCount: 36,
      logoFilePath: '/path/to/sbb.jpg',
      status: 'complete',
    };
    expect(getLogoUrl(company)).toBeNull();
  });

  it('should_returnNull_when_noLogoAvailable', () => {
    const company: SourceCompany = {
      id: 'nologo',
      displayName: 'No Logo Company',
      logo: null,
      url: null,
      speakerCount: 0,
      logoFilePath: null,
      status: 'needs_logo',
    };
    expect(getLogoUrl(company)).toBeNull();
  });

  it('should_returnNull_when_logoUrlIsInvalid', () => {
    const company: SourceCompany = {
      id: 'invalid',
      displayName: 'Invalid URL Company',
      logo: null,
      url: null,
      speakerCount: 0,
      logoFilePath: null,
      status: 'complete',
      logoUrl: 'not-a-valid-url',
    };
    expect(getLogoUrl(company)).toBeNull();
  });
});

describe('transformCompanyForApi', () => {
  it('should_transformBasicCompany_when_allFieldsPresent', () => {
    const company: SourceCompany = {
      id: 'sbb',
      displayName: 'SBB CFF FFS',
      logo: 'sbb.jpg',
      url: 'https://www.sbb.ch',
      speakerCount: 36,
      logoFilePath: '/path/to/sbb.jpg',
      status: 'complete',
    };

    const result = transformCompanyForApi(company);

    expect(result).toEqual({
      name: 'sbb',
      displayName: 'SBB CFF FFS',
      website: 'https://www.sbb.ch',
    });
  });

  it('should_stripNonAlphanumericFromName_when_idContainsSpecialChars', () => {
    const company: SourceCompany = {
      id: 'isc-ejpd',
      displayName: 'ISC-EJPD',
      logo: null,
      url: 'https://www.isc.admin.ch',
      speakerCount: 4,
      logoFilePath: null,
      status: 'complete',
    };

    const result = transformCompanyForApi(company);

    expect(result.name).toBe('iscejpd');
  });

  it('should_cleanFilenameDisplayName_when_displayNameIsFilename', () => {
    const company: SourceCompany = {
      id: 'googlecloud',
      displayName: 'googleCloud.png',
      logo: 'googleCloud.png',
      url: null,
      speakerCount: 2,
      logoFilePath: '/path/to/googleCloud.png',
      status: 'pending_url',
    };

    const result = transformCompanyForApi(company);

    expect(result.displayName).toBe('google Cloud');
  });

  it('should_omitWebsite_when_urlIsNull', () => {
    const company: SourceCompany = {
      id: 'nourl',
      displayName: 'No URL Company',
      logo: null,
      url: null,
      speakerCount: 1,
      logoFilePath: null,
      status: 'pending_url',
    };

    const result = transformCompanyForApi(company);

    expect(result.website).toBeUndefined();
  });

  it('should_omitWebsite_when_urlIsInvalid', () => {
    const company: SourceCompany = {
      id: 'badurl',
      displayName: 'Bad URL Company',
      logo: null,
      url: 'not-a-valid-url',
      speakerCount: 1,
      logoFilePath: null,
      status: 'complete',
    };

    const result = transformCompanyForApi(company);

    expect(result.website).toBeUndefined();
  });
});

describe('parseCompanyJson', () => {
  it('should_parseValidJson_when_arrayProvided', () => {
    const json = JSON.stringify([
      {
        id: 'sbb',
        displayName: 'SBB',
        logo: null,
        url: 'https://sbb.ch',
        speakerCount: 1,
        logoFilePath: null,
        status: 'complete',
      },
    ]);

    const result = parseCompanyJson(json);

    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('sbb');
  });

  it('should_throwError_when_jsonIsInvalid', () => {
    expect(() => parseCompanyJson('not valid json')).toThrow('Invalid JSON');
  });

  it('should_throwError_when_jsonIsNotArray', () => {
    expect(() => parseCompanyJson('{"id": "sbb"}')).toThrow('JSON must be an array');
  });

  it('should_throwError_when_itemMissingId', () => {
    const json = JSON.stringify([{ displayName: 'No ID' }]);
    expect(() => parseCompanyJson(json)).toThrow("missing required 'id' field");
  });

  it('should_throwError_when_itemMissingDisplayName', () => {
    const json = JSON.stringify([{ id: 'test' }]);
    expect(() => parseCompanyJson(json)).toThrow("missing required 'displayName' field");
  });

  it('should_parseMultipleCompanies_when_arrayHasMany', () => {
    const json = JSON.stringify([
      { id: 'a', displayName: 'A' },
      { id: 'b', displayName: 'B' },
      { id: 'c', displayName: 'C' },
    ]);

    const result = parseCompanyJson(json);

    expect(result).toHaveLength(3);
  });
});

describe('createImportCandidates', () => {
  it('should_createCandidates_when_companiesProvided', () => {
    const companies: SourceCompany[] = [
      {
        id: 'sbb',
        displayName: 'SBB',
        logo: null,
        url: 'https://sbb.ch',
        speakerCount: 1,
        logoFilePath: null,
        status: 'complete',
      },
    ];

    const result = createImportCandidates(companies);

    expect(result).toHaveLength(1);
    expect(result[0].source).toBe(companies[0]);
    expect(result[0].apiPayload.name).toBe('sbb');
    expect(result[0].importStatus).toBe('pending');
  });

  it('should_includeLogoUrl_when_sourceHasLogoUrl', () => {
    const companies: SourceCompany[] = [
      {
        id: 'puzzle',
        displayName: 'Puzzle',
        logo: null,
        url: 'https://puzzle.ch',
        speakerCount: 1,
        logoFilePath: null,
        status: 'complete',
        logoUrl: 'https://puzzle.ch/logo.svg',
      },
    ];

    const result = createImportCandidates(companies);

    expect(result[0].logoUrl).toBe('https://puzzle.ch/logo.svg');
  });
});

describe('isDuplicateName', () => {
  it('should_returnTrue_when_nameExists', () => {
    expect(isDuplicateName('sbb', ['sbb', 'aws', 'ibm'])).toBe(true);
  });

  it('should_returnTrue_when_nameExistsDifferentCase', () => {
    expect(isDuplicateName('SBB', ['sbb', 'aws', 'ibm'])).toBe(true);
    expect(isDuplicateName('sbb', ['SBB', 'AWS', 'IBM'])).toBe(true);
  });

  it('should_returnFalse_when_nameNotExists', () => {
    expect(isDuplicateName('google', ['sbb', 'aws', 'ibm'])).toBe(false);
  });

  it('should_returnFalse_when_listEmpty', () => {
    expect(isDuplicateName('sbb', [])).toBe(false);
  });
});
