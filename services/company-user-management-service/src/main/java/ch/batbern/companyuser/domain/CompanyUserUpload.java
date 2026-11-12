package ch.batbern.companyuser.domain;

import ch.batbern.shared.upload.UploadRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An upload whose purpose this service owns, e.g. {@code company-logo} (Story 17.11). */
@Entity
@Table(name = "company_user_uploads")
public class CompanyUserUpload extends UploadRecord {
}
