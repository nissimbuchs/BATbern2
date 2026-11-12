package ch.batbern.events.domain;

import ch.batbern.shared.upload.UploadRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An upload whose purpose this service owns, e.g. {@code event-theme-image} (Story 17.11). */
@Entity
@Table(name = "event_management_uploads")
public class EventManagementUpload extends UploadRecord {
}
