import { describe, expect, it } from 'vitest';
import { eventCodeOfRegistrationCode } from './registrationCode';

describe('eventCodeOfRegistrationCode', () => {
  it('should_returnThePrefix_when_codeIsWhatEmsCreates', () => {
    expect(eventCodeOfRegistrationCode('BATbern142-reg-A3X9K2')).toBe('BATbern142');
  });

  it('should_returnThePrefix_when_codeIsAnOldHexCode', () => {
    expect(eventCodeOfRegistrationCode('BATbern57-reg-0f3a9c1d')).toBe('BATbern57');
  });

  it('should_mapTheEventNumber_when_codeIsFromTheDataLoad', () => {
    expect(eventCodeOfRegistrationCode('BAT60-EXIST-17')).toBe('BATbern60');
    expect(eventCodeOfRegistrationCode('BAT60-NEW-3')).toBe('BATbern60');
  });

  it('should_returnNull_when_codeHasNoKnownShape', () => {
    // The page used to read a year from `BAT-YYYY-NNNNNN`, a shape no stored code has.
    expect(eventCodeOfRegistrationCode('BAT-2025-000123')).toBeNull();
    expect(eventCodeOfRegistrationCode('')).toBeNull();
  });
});
