package ch.batbern.events.mailcenter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Story 16.6: where an organizer opens a conversation. On the event's mail page, or on the global
 * Mail page (Story 16.7) for a conversation without an event.
 */
@Component
public class ConversationLinks {

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl = "https://batbern.ch";

    public String link(String eventCode, String conversationKey) {
        String key = URLEncoder.encode(conversationKey, StandardCharsets.UTF_8);
        if (eventCode == null) {
            return baseUrl + "/organizer/mail?conversation=" + key;
        }
        return baseUrl + "/organizer/events/" + eventCode + "?tab=communications&conversation=" + key;
    }
}
