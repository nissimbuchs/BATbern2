package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.shared.upload.UploadRecordRepository;
import org.springframework.stereotype.Repository;

/** The uploads of this service (table {@code company_user_uploads}). */
@Repository
public interface CompanyUserUploadRepository extends UploadRecordRepository<CompanyUserUpload> {
}
