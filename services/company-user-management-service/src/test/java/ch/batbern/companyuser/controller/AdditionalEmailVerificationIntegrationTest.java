package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.AdditionalEmailVerificationTokenService;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Additional-email verification (v2) — integration tests for the verification
 * flow: send-on-add, resend, GET-check, POST-confirm. Covers the spec's full
 * I/O &amp; Edge-Case Matrix.
 *
 * <p>{@link EmailService} is mocked via {@link TestAwsConfig} ({@code @Primary}).
 * Each test resets the mock and stubs {@code replaceVariables} to call the real
 * implementation so the captured HTML body contains the rendered token link. No
 * real outbound email is ever sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Additional-email verification (v2) — endpoints")
class AdditionalEmailVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;

    @Autowired
    private EmailService emailService; // Mockito mock from TestAwsConfig

    @Autowired
    private AdditionalEmailVerificationTokenService tokenService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private User caller;
    private User other;

    @BeforeEach
    void setUp() {
        Mockito.reset(emailService);
        // Real template rendering so the captured HTML carries the token link.
        Mockito.when(emailService.replaceVariables(anyString(), any())).thenCallRealMethod();

        caller = userRepository.save(User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("john.doe")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build());

        other = userRepository.save(User.builder()
                .username("mary.smith")
                .email("mary.smith@example.com")
                .firstName("Mary")
                .lastName("Smith")
                .cognitoUserId("mary.smith")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build());
    }

    // ---------------------- add → sends verification email ----------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_sendVerificationEmailWithTokenLink_when_additionalEmailAdded")
    void should_sendVerificationEmailWithTokenLink_when_additionalEmailAdded() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"box@example.com\" }"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(to.capture(), subject.capture(), html.capture());

        assertThat(to.getValue()).isEqualTo("box@example.com");
        assertThat(html.getValue()).contains("/verify-email?token=");
        // The rendered link must carry a non-empty token.
        assertThat(html.getValue()).doesNotContain("token={{token}}");
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_still201AndWarn_when_emailServiceThrowsOnAdd")
    void should_still201AndWarn_when_emailServiceThrowsOnAdd() throws Exception {
        Mockito.doThrow(new RuntimeException("SES down"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"box@example.com\" }"))
                .andExpect(status().isCreated());

        // The row was still persisted despite the send failure.
        assertThat(additionalEmailRepository.existsByEmailIgnoreCase("box@example.com")).isTrue();
    }

    @Test
    @WithMockUser(username = "hans.mueller")
    @DisplayName("should_useGermanSubject_when_userLanguagePreferenceIsDe")
    void should_useGermanSubject_when_userLanguagePreferenceIsDe() throws Exception {
        userRepository.save(User.builder()
                .username("hans.mueller")
                .email("hans.mueller@example.com")
                .firstName("Hans")
                .lastName("Mueller")
                .cognitoUserId("hans.mueller")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .preferences(ch.batbern.companyuser.domain.UserPreferences.builder()
                        .language("de")
                        .build())
                .build());

        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"box-de@example.com\" }"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo("Bestätigen Sie Ihre BATbern E-Mail-Adresse");
    }

    @Test
    @WithMockUser(username = "pierre.dupont")
    @DisplayName("should_useEnglishSubject_when_userLanguageIsNonGerman")
    void should_useEnglishSubject_when_userLanguageIsNonGerman() throws Exception {
        // A non-German language preference (fr) falls back to the English template/subject
        // per the email-template DE+EN-only localization rule. (The User entity defaults a
        // missing preferences row to language="de", so an explicit non-de language is the
        // way to exercise the English fallback.)
        userRepository.save(User.builder()
                .username("pierre.dupont")
                .email("pierre.dupont@example.com")
                .firstName("Pierre")
                .lastName("Dupont")
                .cognitoUserId("pierre.dupont")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .preferences(ch.batbern.companyuser.domain.UserPreferences.builder()
                        .language("fr")
                        .build())
                .build());

        mockMvc.perform(post("/api/v1/users/me/additional-emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"box-en@example.com\" }"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo("Confirm your BATbern email address");
    }

    // ---------------------- GET verify (check) ----------------------

    @Test
    @DisplayName("should_returnMaskedEmailAndStatus_when_checkValidToken")
    void should_returnMaskedEmailAndStatus_when_checkValidToken() throws Exception {
        UserAdditionalEmail row = persistAdditional(caller, "box@example.com", null);
        String token = tokenService.generateToken(row.getId(), row.getEmail());

        mockMvc.perform(get("/api/v1/users/additional-emails/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.containsString("***")))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    @DisplayName("should_return400TokenInvalid_when_checkTamperedToken")
    void should_return400TokenInvalid_when_checkTamperedToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/additional-emails/verify").param("token", "not.a.jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("should_return400AndNever500_when_checkEmptyToken")
    void should_return400AndNever500_when_checkEmptyToken() throws Exception {
        // An empty token must map to TOKEN_INVALID (400), never a 500.
        // TOKEN_EXPIRED (real-key expiry) is exercised at the unit level in
        // AdditionalEmailVerificationTokenServiceTest, since the production token
        // bean uses a random key per instance and tokens cannot be cross-signed.
        mockMvc.perform(get("/api/v1/users/additional-emails/verify").param("token", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
    }

    // ---------------------- POST verify (confirm) ----------------------

    @Test
    @DisplayName("should_setVerifiedAt_when_confirmValidToken")
    void should_setVerifiedAt_when_confirmValidToken() throws Exception {
        UserAdditionalEmail row = persistAdditional(caller, "box@example.com", null);
        String token = tokenService.generateToken(row.getId(), row.getEmail());

        mockMvc.perform(post("/api/v1/users/additional-emails/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"" + token + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.alreadyVerified").value(false));

        UserAdditionalEmail reloaded = additionalEmailRepository.findById(row.getId()).orElseThrow();
        assertThat(reloaded.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_returnAlreadyVerified_when_confirmAlreadyVerifiedRow")
    void should_returnAlreadyVerified_when_confirmAlreadyVerifiedRow() throws Exception {
        UserAdditionalEmail row = persistAdditional(caller, "box@example.com", Instant.now());
        String token = tokenService.generateToken(row.getId(), row.getEmail());

        mockMvc.perform(post("/api/v1/users/additional-emails/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"" + token + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.alreadyVerified").value(true));
    }

    @Test
    @DisplayName("should_return404_when_confirmTokenForDeletedRow")
    void should_return404_when_confirmTokenForDeletedRow() throws Exception {
        UserAdditionalEmail row = persistAdditional(caller, "box@example.com", null);
        // Sibling row on the same profile that must remain untouched by the 404.
        UserAdditionalEmail sibling = persistAdditional(caller, "sibling@example.com", null);
        UUID siblingId = sibling.getId();
        // Token for the original (about-to-be-deleted) row id.
        String token = tokenService.generateToken(row.getId(), row.getEmail());
        UUID deletedId = row.getId();

        // Detach everything first so the still-managed caller.additionalEmails
        // collection cannot cascade-re-persist the row we are about to delete.
        entityManager.clear();
        additionalEmailRepository.deleteById(deletedId);
        additionalEmailRepository.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/users/additional-emails/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"" + token + "\" }"))
                .andExpect(status().isNotFound());

        // The unrelated sibling row must be unaffected — still present, still unverified.
        UserAdditionalEmail reloadedSibling = additionalEmailRepository.findById(siblingId).orElseThrow();
        assertThat(reloadedSibling.getVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("should_return404_when_confirmStaleTokenAfterReAdd")
    void should_return404_when_confirmStaleTokenAfterReAdd() throws Exception {
        UserAdditionalEmail original = persistAdditional(caller, "box@example.com", null);
        String staleToken = tokenService.generateToken(original.getId(), original.getEmail());
        UUID originalId = original.getId();

        // Detach first so the managed caller.additionalEmails collection cannot
        // cascade-re-persist the deleted row, then delete + flush the removal so the
        // re-insert below does not collide on the LOWER(email) unique index.
        entityManager.clear();
        additionalEmailRepository.deleteById(originalId);
        additionalEmailRepository.flush();
        entityManager.clear();

        caller = userRepository.findByUsername("john.doe").orElseThrow();
        UserAdditionalEmail readded = persistAdditional(caller, "box@example.com", null);
        assertThat(readded.getId()).isNotEqualTo(originalId);

        // The stale token (old UUID) must 404 — row-UUID binding invalidation.
        mockMvc.perform(post("/api/v1/users/additional-emails/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"" + staleToken + "\" }"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return400TokenInvalid_when_confirmTamperedToken")
    void should_return400TokenInvalid_when_confirmTamperedToken() throws Exception {
        mockMvc.perform(post("/api/v1/users/additional-emails/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \"garbage\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_INVALID"));
    }

    // ---------------------- resend ----------------------

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return204AndSendEmail_when_resendForUnverifiedOwnEmail")
    void should_return204AndSendEmail_when_resendForUnverifiedOwnEmail() throws Exception {
        persistAdditional(caller, "box@example.com", null);

        mockMvc.perform(post("/api/v1/users/me/additional-emails/{email}/resend-verification",
                        "box@example.com"))
                .andExpect(status().isNoContent());

        verify(emailService, times(1)).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return404_when_resendForEmailNotOwned")
    void should_return404_when_resendForEmailNotOwned() throws Exception {
        // belongs to mary.smith, not john.doe
        persistAdditional(other, "shared@example.com", null);

        mockMvc.perform(post("/api/v1/users/me/additional-emails/{email}/resend-verification",
                        "shared@example.com"))
                .andExpect(status().isNotFound());

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "john.doe")
    @DisplayName("should_return409AlreadyVerified_when_resendForVerifiedEmail")
    void should_return409AlreadyVerified_when_resendForVerifiedEmail() throws Exception {
        persistAdditional(caller, "box@example.com", Instant.now());

        mockMvc.perform(post("/api/v1/users/me/additional-emails/{email}/resend-verification",
                        "box@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_VERIFIED"));

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_return401_when_resendNotAuthenticated")
    void should_return401_when_resendNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/{email}/resend-verification",
                        "box@example.com"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------- helpers ----------------------

    private UserAdditionalEmail persistAdditional(User owner, String email, Instant verifiedAt) {
        UserAdditionalEmail row = UserAdditionalEmail.builder()
                .email(email)
                .createdAt(Instant.now())
                .verifiedAt(verifiedAt)
                .build();
        owner.addAdditionalEmail(row);
        userRepository.save(owner);
        userRepository.flush();
        // Re-fetch so the generated UUID id is reliably populated on the returned
        // instance (the builder-created reference may not carry it back).
        return additionalEmailRepository.findByUserAndEmailIgnoreCase(owner, email).orElseThrow();
    }
}
