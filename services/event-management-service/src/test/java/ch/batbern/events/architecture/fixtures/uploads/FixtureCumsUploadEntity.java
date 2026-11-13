package ch.batbern.events.architecture.fixtures.uploads;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Breaks "no EMS entity maps an upload table of another service": the uploads of company-user-management. */
@Entity
@Table(name = "company_user_uploads")
public class FixtureCumsUploadEntity {
    @Id
    private Long id;
}
