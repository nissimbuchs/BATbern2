package ch.batbern.events.domain;

/**
 * Session material type enumeration
 * Story 5.9: Session Materials Upload (AC6)
 *
 * Material type categories:
 * - PRESENTATION: PowerPoint, Keynote, ODP
 * - DOCUMENT: PDF, Word, TXT
 * - VIDEO: MP4, MOV, AVI, MKV, WEBM
 * - ARCHIVE: ZIP, TAR.GZ
 * - OTHER: Other file types
 */
public enum SessionMaterialType {
    PRESENTATION,
    DOCUMENT,
    VIDEO,
    ARCHIVE,
    OTHER
}
