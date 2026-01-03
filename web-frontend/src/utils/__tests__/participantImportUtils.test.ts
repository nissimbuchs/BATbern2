import { describe, it, expect } from 'vitest';
import {
  parseParticipantCsv,
  generateSyntheticEmail,
  extractEventParticipation,
  convertParticipantToRegistrationRequest,
} from '../participantImportUtils';
import type { SourceParticipant } from '../../types/participantImport.types';

describe('participantImportUtils', () => {
  // Helper to create CSV data row with exactly 57 event columns
  const createCsvDataRow = (
    name: string,
    first: string,
    last: string,
    email: string,
    company: string,
    eventAttendance: Record<number, string> = {}
  ): string => {
    const eventValues = Array.from({ length: 57 }, (_, i) => eventAttendance[i + 1] || '');
    return `${name},${first},${last},${email},${company},${eventValues.join(',')}`;
  };

  describe('parseParticipantCsv', () => {
    it('should_parseCsv_when_validStructureProvided', () => {
      const header =
        'Name,FirstName,LastName,BestMail,companyKey,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57';
      const dataRow = createCsvDataRow(
        'John Doe',
        'John',
        'Doe',
        'john.doe@example.com',
        'example',
        { 2: '1', 5: '1' }
      );
      const csvContent = `${header}\n${dataRow}`;

      const result = parseParticipantCsv(csvContent);

      expect(result).toHaveLength(1);
      expect(result[0].FirstName).toBe('John');
      expect(result[0].LastName).toBe('Doe');
      expect(result[0].BestMail).toBe('john.doe@example.com');
    });

    it('should_removeBOM_when_bomCharacterPresent', () => {
      const header =
        'Name,FirstName,LastName,BestMail,companyKey,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57';
      const dataRow = createCsvDataRow('Test', 'Test', 'User', 'test@example.com', 'test');
      const csvWithBOM = `\uFEFF${header}\n${dataRow}`;

      const result = parseParticipantCsv(csvWithBOM);

      expect(result).toHaveLength(1);
      expect(result[0].FirstName).toBe('Test');
    });

    it('should_handleGermanCharacters_when_csvContainsUmlauts', () => {
      const header =
        'Name,FirstName,LastName,BestMail,companyKey,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57';
      const dataRow = createCsvDataRow(
        'Müller',
        'Hans',
        'Müller',
        'hans.mueller@example.com',
        'test'
      );
      const csvContent = `${header}\n${dataRow}`;

      const result = parseParticipantCsv(csvContent);

      expect(result).toHaveLength(1);
      expect(result[0].LastName).toBe('Müller');
    });

    it('should_throwError_when_invalidCsvStructure', () => {
      const invalidCsv = 'Name,FirstName,LastName\nJohn,Doe,john@example.com';

      expect(() => parseParticipantCsv(invalidCsv)).toThrow(
        'Missing required columns: BestMail, companyKey'
      );
    });

    it('should_skipEmptyLines_when_csvHasBlankRows', () => {
      const header =
        'Name,FirstName,LastName,BestMail,companyKey,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57';
      const dataRow1 = createCsvDataRow('John', 'John', 'Doe', 'john@example.com', 'test');
      const dataRow2 = createCsvDataRow('Jane', 'Jane', 'Smith', 'jane@example.com', 'test2');
      const csvWithEmptyLines = `${header}\n${dataRow1}\n\n${dataRow2}`;

      const result = parseParticipantCsv(csvWithEmptyLines);

      expect(result).toHaveLength(2);
    });
  });

  describe('generateSyntheticEmail', () => {
    it('should_generateEmail_when_firstAndLastNameProvided', () => {
      const email = generateSyntheticEmail('John', 'Doe');

      expect(email).toBe('john.doe@batbern.ch');
    });

    it('should_convertGermanCharacters_when_umlauts inName', () => {
      const email = generateSyntheticEmail('Jürgen', 'Müller');

      expect(email).toBe('juergen.mueller@batbern.ch');
    });

    it('should_handleAllGermanCharacters_when_variousUmlautsPresent', () => {
      expect(generateSyntheticEmail('Ärzte', 'Äpfel')).toBe('aerzte.aepfel@batbern.ch');
      expect(generateSyntheticEmail('Öffner', 'Ö test')).toBe('oeffner.oetest@batbern.ch');
      expect(generateSyntheticEmail('Über', 'Ü test')).toBe('ueber.uetest@batbern.ch');
      expect(generateSyntheticEmail('Straße', 'Süß')).toBe('strasse.suess@batbern.ch');
    });

    it('should_removeSpecialCharacters_when_nameContainsNonAlphanumeric', () => {
      const email = generateSyntheticEmail('Jean-Paul', "O'Brien");

      expect(email).toBe('jeanpaul.obrien@batbern.ch');
    });

    it('should_throwError_when_bothNamesEmpty', () => {
      expect(() => generateSyntheticEmail('', '')).toThrow(
        'Cannot construct username: both first name and last name are empty'
      );
    });

    it('should_useFallback_when_firstNameEmpty', () => {
      const email = generateSyntheticEmail('', 'Smith');

      expect(email).toBe('unknown.smith@batbern.ch');
    });

    it('should_useFallback_when_lastNameEmpty', () => {
      const email = generateSyntheticEmail('John', '');

      expect(email).toBe('john.unknown@batbern.ch');
    });
  });

  describe('extractEventParticipation', () => {
    it('should_extractEventCodes_when_participantAttendedEvents', () => {
      const participant: SourceParticipant = {
        Name: 'Test',
        FirstName: 'Test',
        LastName: 'User',
        BestMail: 'test@example.com',
        companyKey: 'test',
        '1': '',
        '2': '1',
        '3': '',
        '5': '1',
        '25': '1',
        '57': '1',
      };

      // Fill in missing event columns (4-56)
      for (let i = 4; i <= 56; i++) {
        if (!participant[i.toString()]) {
          participant[i.toString()] = '';
        }
      }

      const eventCodes = extractEventParticipation(participant);

      expect(eventCodes).toEqual(['BATbern2', 'BATbern5', 'BATbern25', 'BATbern57']);
    });

    it('should_returnEmptyArray_when_noEventsAttended', () => {
      const participant: SourceParticipant = {
        Name: 'Test',
        FirstName: 'Test',
        LastName: 'User',
        BestMail: 'test@example.com',
        companyKey: 'test',
      };

      // Add all event columns as empty
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = '';
      }

      const eventCodes = extractEventParticipation(participant);

      expect(eventCodes).toEqual([]);
    });

    it('should_handleAllEvents_when_participantAttendedAll57Events', () => {
      const participant: SourceParticipant = {
        Name: 'Test',
        FirstName: 'Test',
        LastName: 'User',
        BestMail: 'test@example.com',
        companyKey: 'test',
      };

      // Mark all events as attended
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = '1';
      }

      const eventCodes = extractEventParticipation(participant);

      expect(eventCodes).toHaveLength(57);
      expect(eventCodes[0]).toBe('BATbern1');
      expect(eventCodes[56]).toBe('BATbern57');
    });
  });

  describe('convertParticipantToRegistrationRequest', () => {
    it('should_convertToRequest_when_validParticipantProvided', () => {
      const participant: SourceParticipant = {
        Name: 'John Doe',
        FirstName: 'John',
        LastName: 'Doe',
        BestMail: 'john.doe@example.com',
        companyKey: 'example',
      };

      // Add event attendance
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = i === 2 || i === 5 || i === 25 ? '1' : '';
      }

      const request = convertParticipantToRegistrationRequest(participant);

      expect(request.participantEmail).toBe('john.doe@example.com');
      expect(request.firstName).toBe('John');
      expect(request.lastName).toBe('Doe');
      expect(request.registrations).toHaveLength(3);
      expect(request.registrations[0]).toEqual({
        eventCode: 'BATbern2',
        status: 'ATTENDED',
      });
    });

    it('should_useSyntheticEmail_when_emailMissing', () => {
      const participant: SourceParticipant = {
        Name: 'Jane Smith',
        FirstName: 'Jane',
        LastName: 'Smith',
        BestMail: '',
        companyKey: 'test',
      };

      // Add event attendance
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = i === 10 ? '1' : '';
      }

      const request = convertParticipantToRegistrationRequest(participant);

      expect(request.participantEmail).toBe('jane.smith@batbern.ch');
      expect(request.registrations).toHaveLength(1);
    });

    it('should_handleGermanCharactersInSyntheticEmail_when_nameHasUmlauts', () => {
      const participant: SourceParticipant = {
        Name: 'Müller',
        FirstName: 'Hans',
        LastName: 'Müller',
        BestMail: '  ',
        companyKey: 'test',
      };

      // Add event attendance
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = i === 1 ? '1' : '';
      }

      const request = convertParticipantToRegistrationRequest(participant);

      expect(request.participantEmail).toBe('hans.mueller@batbern.ch');
    });

    it('should_returnEmptyRegistrations_when_noEventsAttended', () => {
      const participant: SourceParticipant = {
        Name: 'Test User',
        FirstName: 'Test',
        LastName: 'User',
        BestMail: 'test@example.com',
        companyKey: 'test',
      };

      // No events attended
      for (let i = 1; i <= 57; i++) {
        participant[i.toString()] = '';
      }

      const request = convertParticipantToRegistrationRequest(participant);

      expect(request.registrations).toHaveLength(0);
    });
  });
});
