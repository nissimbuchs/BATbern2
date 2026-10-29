package ch.batbern.companyuser.specification;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the paginated user list, composed in SQL so that paging, {@code totalItems} and
 * every filter agree (#1153). The active filter used to run in Java on the already-cut page,
 * which returned short or empty pages while matching users sat on other pages. An absent
 * filter is {@link Specification#unrestricted()}: Spring Data 4 rejects null specifications.
 */
public final class UserSpecification {

    private UserSpecification() {
    }

    /** Case-insensitive substring over username, email, first/last name and company id. */
    public static Specification<User> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(root.get("companyId")), pattern));
    }

    public static Specification<User> hasRole(Role role) {
        if (role == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.isMember(role, root.get("roles"));
    }

    public static Specification<User> inCompany(String companyId) {
        if (companyId == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<User> isActive(Boolean active) {
        if (active == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("isActive"), active);
    }
}
