package ch.batbern.companyuser.service;

import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Purpose {@code company-logo} (Story 17.11): the logo of a company. Without a target, any signed-in
 * user may upload (the company create form, a logo request); the file does nothing until an entity
 * the caller may change takes it. With a target {@code companyName} the logo is replaced at once,
 * which only an organizer may do (as {@code PATCH /companies/{name}}). Import from a URL is the
 * organizer's batch import.
 */
@Component
public class CompanyLogoUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "company-logo";
    public static final String TARGET_TYPE = "COMPANY";

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    /** {@code @Lazy}: CompanyService uses the upload service, which holds this handler. */
    public CompanyLogoUploadHandler(CompanyRepository companyRepository, @Lazy CompanyService companyService) {
        this.companyRepository = companyRepository;
        this.companyService = companyService;
    }

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public UploadRules rules() {
        return UploadRules.images5Mb();
    }

    @Override
    public String targetKey() {
        return "companyName";
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public void checkTarget(String companyName) {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER")) {
            throw UploadException.forbidden("Only organizers replace the logo of a company");
        }
        if (!companyRepository.existsByName(companyName)) {
            throw UploadException.notFound("Company not found: " + companyName);
        }
    }

    @Override
    public String finalKey(UploadRecord upload, String companyName) {
        return logoKey(companyName, upload);
    }

    /** Unchanged layout of every company logo: {@code logos/{year}/companies/{name}/logo-{uploadId}.{ext}}. */
    public static String logoKey(String companyName, UploadRecord upload) {
        return String.format("logos/%d/companies/%s/logo-%s.%s", LocalDate.now(ZoneOffset.UTC).getYear(),
                companyName, upload.getUploadId(), upload.getFileExtension());
    }

    @Override
    public void attach(UploadRecord upload, String companyName) {
        companyService.applyLogo(companyName, upload);
    }

    @Override
    public boolean supportsImport() {
        return true;
    }

    @Override
    public void checkMayImport() {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER")) {
            throw UploadException.forbidden("Only organizers import logos");
        }
    }
}
