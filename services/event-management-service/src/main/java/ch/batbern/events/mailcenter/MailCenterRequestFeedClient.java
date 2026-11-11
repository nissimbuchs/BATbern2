package ch.batbern.events.mailcenter;

import ch.batbern.shared.security.S2sHeaders;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Reads company-user-management's change feed of merge and logo requests,
 * {@code GET /internal/mail-center-requests} ({@code docs/api/internal/cums-internal.openapi.yml}).
 * In the mail center module, not in {@code client/}, because only the Mail Center uses it.
 * Headers from {@link S2sHeaders}: the job runs without a user, so no JWT is sent. A failed call
 * throws; {@link MailCenterRequestFeedJob} keeps its cursor and tries again on the next poll.
 */
@Component
@RequiredArgsConstructor
public class MailCenterRequestFeedClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.base-url}")
    private String cumsBaseUrl;

    /** One page after {@code since} ({@code null}: the provider's start, see the spec). */
    public Page fetch(String since, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(cumsBaseUrl + "/internal/mail-center-requests")
                .queryParam("limit", limit);
        URI url = since == null
                ? builder.build().encode().toUri()
                : builder.queryParam("since", "{since}").encode().buildAndExpand(since).toUri();
        Page page = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(S2sHeaders.forCurrentUser()),
                Page.class).getBody();
        if (page == null || page.items() == null || page.nextCursor() == null) {
            throw new IllegalStateException("Mail Center request feed answered without items or cursor");
        }
        return page;
    }

    /** The request kind; maps onto {@link RequestConversations.Kind}. */
    public enum Kind { MERGE, LOGO }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Kind kind, UUID requestId, String status, String requesterName, String requesterEmail,
            String subject, String note, String decidedBy, String changedAt) {

        public boolean open() {
            return "OPEN".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(List<Item> items, String nextCursor) {
    }
}
