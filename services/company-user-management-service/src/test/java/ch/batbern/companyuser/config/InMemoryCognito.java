package ch.batbern.companyuser.config;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InternalErrorException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The test stand-in for the Cognito user pool (Epic 17 test fidelity): an in-memory
 * {@link CognitoIdentityProviderClient}, so the real {@code CognitoIntegrationServiceImpl}, the
 * reconciliation and the confirmation-resend job run in every integration test, as the
 * {@code SesRecorder} stands in for SES. It is the boundary, not our code.
 *
 * <p>It behaves like the BATbern pool ({@code infrastructure/lib/stacks/cognito-stack.ts}) for the
 * operations CUMS calls: the e-mail is the sign-in alias (the Cognito username is the generated
 * {@code sub}, and every {@code Admin*} call accepts the e-mail or the sub, case-sensitive as the
 * pool's default), the pool's password policy (8 characters, lower, upper, digit, symbol), and the
 * errors Cognito answers: {@code UsernameExistsException} for a taken e-mail on create,
 * {@code UserNotFoundException} for an unknown user, {@code AliasExistsException} when an e-mail
 * moves onto another login's address, {@code InvalidPasswordException} for a weak password. Every
 * other operation keeps the SDK default and throws {@link UnsupportedOperationException}.
 *
 * <p>Usage: {@code TestAwsConfig} registers one instance as the context's
 * {@code CognitoIdentityProviderClient}. Tests autowire it, {@link #reset()} it in
 * {@code @BeforeEach}, seed logins with {@link #givenLogin(String, UserStatusType)}, make one call
 * fail with {@link #failNext(Operation, CognitoIdentityProviderException)} and assert the effect
 * ({@link #login(String)}, {@link #confirmationCodesResent()}), never a call on our service.
 */
public final class InMemoryCognito implements CognitoIdentityProviderClient {

    /** The operations CUMS calls; {@link #failNext} takes one of them. */
    public enum Operation {
        ADMIN_CREATE_USER, ADMIN_GET_USER, ADMIN_SET_USER_PASSWORD, ADMIN_UPDATE_USER_ATTRIBUTES,
        ADMIN_DISABLE_USER, ADMIN_ENABLE_USER, ADMIN_DELETE_USER, LIST_USERS, RESEND_CONFIRMATION_CODE
    }

    /** One login of the pool, as Cognito holds it. */
    public static final class Login {
        private final String sub;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final Instant created;
        private UserStatusType status;
        private boolean enabled = true;
        private String password;
        private boolean passwordPermanent;

        private Login(String sub, Instant created, UserStatusType status) {
            this.sub = sub;
            this.created = created;
            this.status = status;
            this.attributes.put("sub", sub);
        }

        public String sub() {
            return sub;
        }

        public String email() {
            return attributes.get("email");
        }

        public String attribute(String name) {
            return attributes.get(name);
        }

        public Map<String, String> attributes() {
            return Map.copyOf(attributes);
        }

        public UserStatusType status() {
            return status;
        }

        public boolean enabled() {
            return enabled;
        }

        public String password() {
            return password;
        }

        public boolean passwordPermanent() {
            return passwordPermanent;
        }

        public Instant created() {
            return created;
        }

        private UserType toUserType() {
            return UserType.builder().username(sub).attributes(attributeTypes()).userStatus(status)
                    .enabled(enabled).userCreateDate(created).userLastModifiedDate(created).build();
        }

        private List<AttributeType> attributeTypes() {
            return attributes.entrySet().stream()
                    .map(e -> AttributeType.builder().name(e.getKey()).value(e.getValue()).build())
                    .toList();
        }
    }

    private final Map<String, Login> loginsBySub = new LinkedHashMap<>();
    private final Map<Operation, Deque<CognitoIdentityProviderException>> failures = new EnumMap<>(Operation.class);
    private final List<String> confirmationCodesResent = new ArrayList<>();

    /** Forgets every login, failure and recorded resend. */
    public synchronized void reset() {
        loginsBySub.clear();
        failures.clear();
        confirmationCodesResent.clear();
    }

    /** The next call of {@code operation} throws {@code exception} (once per call of this method). */
    public synchronized void failNext(Operation operation, CognitoIdentityProviderException exception) {
        failures.computeIfAbsent(operation, o -> new ArrayDeque<>()).add(exception);
    }

    /** A login that already exists in the pool (sign-up or an earlier provisioning). */
    public synchronized Login givenLogin(String email, UserStatusType status) {
        return givenLogin(email, status, Instant.now());
    }

    /** As {@link #givenLogin(String, UserStatusType)}, created at {@code created}. */
    public synchronized Login givenLogin(String email, UserStatusType status, Instant created) {
        if (findByAlias(email).isPresent()) {
            throw new IllegalStateException("The pool already has a login for " + email);
        }
        Login login = new Login(UUID.randomUUID().toString(), created, status);
        login.attributes.put("email", email);
        login.attributes.put("email_verified", "true");
        loginsBySub.put(login.sub, login);
        return login;
    }

    /** The login for an e-mail or a sub, as an {@code Admin*} call would resolve it. */
    public synchronized Optional<Login> login(String emailOrSub) {
        return resolve(emailOrSub);
    }

    public synchronized List<Login> logins() {
        return List.copyOf(loginsBySub.values());
    }

    /** The usernames {@code ResendConfirmationCode} was called for, in order. */
    public synchronized List<String> confirmationCodesResent() {
        return List.copyOf(confirmationCodesResent);
    }

    /** A Cognito {@code InternalErrorException}, for {@link #failNext}. */
    public static CognitoIdentityProviderException internalError(String message) {
        return (CognitoIdentityProviderException) InternalErrorException.builder().message(message).statusCode(500)
                .awsErrorDetails(details("InternalErrorException", message)).build();
    }

    // --- the SDK operations -----------------------------------------------------------------

    @Override
    public synchronized AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest request) {
        failIfAsked(Operation.ADMIN_CREATE_USER);
        Map<String, String> attributes = new LinkedHashMap<>();
        request.userAttributes().forEach(a -> attributes.put(a.name(), a.value()));
        String email = attributes.getOrDefault("email", request.username());
        if (request.username() == null || !request.username().contains("@") || email == null) {
            throw invalidParameter("Username should be an email.");
        }
        if (findByAlias(email).isPresent()) {
            throw (UsernameExistsException) UsernameExistsException.builder()
                    .message("An account with the given email already exists.").statusCode(400)
                    .awsErrorDetails(details("UsernameExistsException",
                            "An account with the given email already exists."))
                    .build();
        }
        if (request.temporaryPassword() != null) {
            checkPolicy(request.temporaryPassword());
        }
        Login login = new Login(UUID.randomUUID().toString(), Instant.now(), UserStatusType.FORCE_CHANGE_PASSWORD);
        login.attributes.putAll(attributes);
        login.attributes.put("email", email);
        login.attributes.put("sub", login.sub);
        login.password = request.temporaryPassword();
        loginsBySub.put(login.sub, login);
        return AdminCreateUserResponse.builder().user(login.toUserType()).build();
    }

    @Override
    public synchronized AdminGetUserResponse adminGetUser(AdminGetUserRequest request) {
        failIfAsked(Operation.ADMIN_GET_USER);
        Login login = existing(request.username());
        return AdminGetUserResponse.builder().username(login.sub).userAttributes(login.attributeTypes())
                .userStatus(login.status).enabled(login.enabled).userCreateDate(login.created)
                .userLastModifiedDate(login.created).build();
    }

    @Override
    public synchronized AdminSetUserPasswordResponse adminSetUserPassword(AdminSetUserPasswordRequest request) {
        failIfAsked(Operation.ADMIN_SET_USER_PASSWORD);
        Login login = existing(request.username());
        checkPolicy(request.password());
        login.password = request.password();
        login.passwordPermanent = Boolean.TRUE.equals(request.permanent());
        login.status = login.passwordPermanent ? UserStatusType.CONFIRMED : UserStatusType.FORCE_CHANGE_PASSWORD;
        return AdminSetUserPasswordResponse.builder().build();
    }

    @Override
    public synchronized AdminUpdateUserAttributesResponse adminUpdateUserAttributes(
            AdminUpdateUserAttributesRequest request) {
        failIfAsked(Operation.ADMIN_UPDATE_USER_ATTRIBUTES);
        Login login = existing(request.username());
        for (AttributeType attribute : request.userAttributes()) {
            if ("email".equals(attribute.name())) {
                Optional<Login> holder = findByAlias(attribute.value());
                if (holder.isPresent() && holder.get() != login) {
                    throw (AliasExistsException) AliasExistsException.builder()
                            .message("An account with the given email already exists.").statusCode(400)
                            .awsErrorDetails(details("AliasExistsException",
                                    "An account with the given email already exists."))
                            .build();
                }
            }
            if ("sub".equals(attribute.name())) {
                throw invalidParameter("Cannot modify an immutable attribute: sub");
            }
        }
        request.userAttributes().forEach(a -> login.attributes.put(a.name(), a.value()));
        return AdminUpdateUserAttributesResponse.builder().build();
    }

    @Override
    public synchronized AdminDisableUserResponse adminDisableUser(AdminDisableUserRequest request) {
        failIfAsked(Operation.ADMIN_DISABLE_USER);
        existing(request.username()).enabled = false;
        return AdminDisableUserResponse.builder().build();
    }

    @Override
    public synchronized AdminEnableUserResponse adminEnableUser(AdminEnableUserRequest request) {
        failIfAsked(Operation.ADMIN_ENABLE_USER);
        existing(request.username()).enabled = true;
        return AdminEnableUserResponse.builder().build();
    }

    @Override
    public synchronized AdminDeleteUserResponse adminDeleteUser(AdminDeleteUserRequest request) {
        failIfAsked(Operation.ADMIN_DELETE_USER);
        loginsBySub.remove(existing(request.username()).sub);
        return AdminDeleteUserResponse.builder().build();
    }

    /** Pages by {@code limit} (default and maximum 60, as Cognito); no {@code filter} support. */
    @Override
    public synchronized ListUsersResponse listUsers(ListUsersRequest request) {
        failIfAsked(Operation.LIST_USERS);
        if (request.filter() != null && !request.filter().isBlank()) {
            throw new UnsupportedOperationException("InMemoryCognito does not implement ListUsers filters");
        }
        int limit = request.limit() == null ? 60 : request.limit();
        if (limit < 1 || limit > 60) {
            throw invalidParameter("Limit must be between 1 and 60.");
        }
        List<Login> all = new ArrayList<>(loginsBySub.values());
        int from = request.paginationToken() == null ? 0 : Integer.parseInt(request.paginationToken());
        int to = Math.min(all.size(), from + limit);
        ListUsersResponse.Builder page = ListUsersResponse.builder()
                .users(all.subList(from, to).stream().map(Login::toUserType).toList());
        if (to < all.size()) {
            page.paginationToken(Integer.toString(to));
        }
        return page.build();
    }

    @Override
    public synchronized ResendConfirmationCodeResponse resendConfirmationCode(ResendConfirmationCodeRequest request) {
        failIfAsked(Operation.RESEND_CONFIRMATION_CODE);
        Login login = existing(request.username());
        if (login.status != UserStatusType.UNCONFIRMED) {
            throw invalidParameter("User is already confirmed.");
        }
        confirmationCodesResent.add(request.username());
        return ResendConfirmationCodeResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {
        // nothing to release
    }

    // --- helpers ------------------------------------------------------------------------------

    private void failIfAsked(Operation operation) {
        Deque<CognitoIdentityProviderException> queue = failures.get(operation);
        if (queue != null && !queue.isEmpty()) {
            throw queue.poll();
        }
    }

    private Optional<Login> resolve(String emailOrSub) {
        if (emailOrSub == null) {
            return Optional.empty();
        }
        Login bySub = loginsBySub.get(emailOrSub);
        return bySub != null ? Optional.of(bySub) : findByAlias(emailOrSub);
    }

    private Optional<Login> findByAlias(String email) {
        return loginsBySub.values().stream().filter(l -> email != null && email.equals(l.email())).findFirst();
    }

    private Login existing(String username) {
        return resolve(username).orElseThrow(() -> (UserNotFoundException) UserNotFoundException.builder()
                .message("User does not exist.").statusCode(400)
                .awsErrorDetails(details("UserNotFoundException", "User does not exist.")).build());
    }

    private static void checkPolicy(String password) {
        boolean ok = password != null && password.length() >= 8
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!ok) {
            throw (InvalidPasswordException) InvalidPasswordException.builder()
                    .message("Password does not conform to policy").statusCode(400)
                    .awsErrorDetails(details("InvalidPasswordException", "Password does not conform to policy"))
                    .build();
        }
    }

    private static InvalidParameterException invalidParameter(String message) {
        return (InvalidParameterException) InvalidParameterException.builder().message(message).statusCode(400)
                .awsErrorDetails(details("InvalidParameterException", message)).build();
    }

    private static AwsErrorDetails details(String code, String message) {
        return AwsErrorDetails.builder().errorCode(code).errorMessage(message)
                .serviceName("CognitoIdentityProvider").build();
    }
}
