package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The topic of an event read ({@code include=topics}), topics module. Moved from
 * {@code EventController} unchanged (Story BAT-109); the batch variant loads the topics of a whole
 * page in one query.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class TopicEventExpansion implements EventResponseContributor {

    private final TopicService topicService;
    private final TopicRepository topicRepository;

    @Override
    public Set<String> includes() {
        return Set.of("topics");
    }

    @Override
    public void expand(String include, Event event, EventResponse response, View view) {
        response.setTopic(expandTopic(event));
    }

    @Override
    public void expandBatch(Set<String> includes, List<Event> events, List<EventResponse> responses, View view) {
        if (!includes.contains("topics")) {
            return;
        }
        Set<String> topicCodes = events.stream()
                .map(Event::getTopicCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!topicCodes.isEmpty()) {
            Map<String, Map<String, Object>> topicByCode = topicRepository.findByTopicCodeIn(topicCodes)
                    .stream()
                    .collect(Collectors.toMap(
                            Topic::getTopicCode,
                            t -> {
                                Map<String, Object> m = new HashMap<>();
                                m.put("code", t.getTopicCode());
                                m.put("name", t.getTitle());
                                m.put("description", t.getDescription());
                                m.put("category", t.getCategory());
                                return m;
                            }
                    ));

            for (int i = 0; i < events.size(); i++) {
                String code = events.get(i).getTopicCode();
                if (code != null) {
                    responses.get(i).setTopic(topicByCode.get(code));
                }
            }
        }
    }

    private Map<String, Object> expandTopic(Event event) {
        if (event.getTopicCode() == null) {
            return null;
        }

        // Fetch topic from repository
        return topicService.getTopicByCode(event.getTopicCode())
                .map(topic -> {
                    Map<String, Object> topicMap = new HashMap<>();
                    topicMap.put("code", topic.getTopicCode());
                    topicMap.put("name", topic.getTitle());
                    topicMap.put("description", topic.getDescription());
                    topicMap.put("category", topic.getCategory());
                    return topicMap;
                })
                .orElse(null);
    }
}
