import { describe, it, expect } from 'vitest';
import { isPublicByDefault } from '@/utils/materialVisibility';

describe('isPublicByDefault', () => {
  it('should_bePublic_when_fileIsPdfByExtensionOrType', () => {
    expect(isPublicByDefault('Folien Müller.pdf', 'application/pdf')).toBe(true);
    expect(isPublicByDefault('SLIDES.PDF', '')).toBe(true);
    expect(isPublicByDefault('handout', 'Application/PDF')).toBe(true);
  });

  it('should_notBePublic_when_fileIsNoPdf', () => {
    expect(
      isPublicByDefault(
        'Vortrag.pptx',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation'
      )
    ).toBe(false);
    expect(isPublicByDefault('talk.key', null)).toBe(false);
    expect(isPublicByDefault('pdf.zip', 'application/zip')).toBe(false);
  });
});
