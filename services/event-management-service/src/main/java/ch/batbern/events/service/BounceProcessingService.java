package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * SQS listener for SES bounce/complaint processing (Story 10.29 — AC5).
 *
 * <p>Data flow:
 * <ol>
 *   <li>SES detects bounce/complaint on newsletter Configuration Set</li>
 *   <li>SES sends event to SNS topic</li>
 *   <li>SNS forwards to SQS bounce queue</li>
 *   <li>This listener processes the SNS-wrapped SES notification</li>
 *   <li>Updates subscriber suppression state + recipient bounce records</li>
 * </ol>
 *
 * <p>This bean is only loaded when {@code aws.ses.bounce.enabled=true} (staging/production).
 *
 * <p><strong>Idempotency:</strong> Uses GREATEST/COALESCE semantics — reprocessing the same
 * bounce does not double-count or overwrite an earlier suppression timestamp.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "aws.ses.bounce.enabled", havingValue = "true", matchIfMissing = false)
public class BounceProcessingService {

    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterRecipientRepository recipientRepository;
    private final ObjectMapper objectMapper;
    private final int softBounceThreshold;

    public BounceProcessingService(
            NewsletterSubscriberRepository subscriberRepository,
            NewsletterRecipientRepository recipientRepository,
            ObjectMapper objectMapper,
            @Value("${aws.ses.bounce.soft-threshold:3}") int softBounceThreshold) {
        this.subscriberRepository = subscriberRepository;
        this.recipientRepository = recipientRepository;
        this.objectMapper = objectMapper;
        this.softBounceThreshold = softBounceThreshold;
    }

    @SqsListener("${aws.ses.bounce.queue-url}")
    public void handleBounceNotification(String messageBody) {
        try {
            // Parse SNS envelope
            JsonNode snsEnvelope = objectMapper.readTree(messageBody);
            String type = snsEnvelope.path("Type").asText("");

            if (!"Notification".equals(type)) {
                log.debug("Ignoring non-Notification SNS message type: {}", type);
                return;
            }

            // Extract SES notification from the Message field (JSON string within JSON)
            String messageJson = snsEnvelope.path("Message").asText();
            JsonNode sesNotification = objectMapper.readTree(messageJson);
            String notificationType = sesNotification.path("notificationType").asText("");

            switch (notificationType) {
                case "Bounce" -> handleBounce(sesNotification);
                case "Complaint" -> handleComplaint(sesNotification);
                default -> log.debug("Ignoring unknown SES notification type: {}", notificationType);
            }
        } catch (Exception e) {
            log.error("Failed to process bounce notification: {}", e.getMessage(), e);
            throw new RuntimeException("Bounce processing failed", e);
        }
    }

    private void handleBounce(JsonNode sesNotification) {
        JsonNode bounce = sesNotification.path("bounce");
        String bounceType = bounce.path("bounceType").asText("");
        JsonNode recipients = bounce.path("bouncedRecipients");
        UUID sendId = extractSendId(sesNotification);

        for (JsonNode recipient : recipients) {
            String email = recipient.path("emailAddress").asText("");
            if (email.isBlank()) {
                continue;
            }

            if ("Permanent".equals(bounceType)) {
                handleHardBounce(email, sendId);
            } else if ("Transient".equals(bounceType)) {
                handleSoftBounce(email, sendId);
            } else {
                log.info("Bounce event for {} with type '{}' — treating as soft bounce", email, bounceType);
                handleSoftBounce(email, sendId);
            }
        }
    }

    @Transactional
    void handleHardBounce(String email, UUID sendId) {
        log.info("Hard bounce for {}", email);
        Optional<NewsletterSubscriber> optSub = subscriberRepository.findByEmail(email);
        if (optSub.isEmpty()) {
            log.warn("Hard bounce for unknown subscriber: {}", email);
            return;
        }

        NewsletterSubscriber sub = optSub.get();
        Instant now = Instant.now();

        sub.setBounceType("hard");
        sub.setBounceCount(Math.max(sub.getBounceCount(), sub.getBounceCount() + 1));
        sub.setLastBouncedAt(now);
        // Suppress immediately — COALESCE: don't overwrite earlier suppression
        if (sub.getSuppressedAt() == null) {
            sub.setSuppressedAt(now);
        }
        subscriberRepository.save(sub);

        updateRecipientBounce(email, sendId, "hard", now);
    }

    @Transactional
    void handleSoftBounce(String email, UUID sendId) {
        log.info("Soft bounce for {}", email);
        Optional<NewsletterSubscriber> optSub = subscriberRepository.findByEmail(email);
        if (optSub.isEmpty()) {
            log.warn("Soft bounce for unknown subscriber: {}", email);
            return;
        }

        NewsletterSubscriber sub = optSub.get();
        Instant now = Instant.now();

        sub.setBounceType("soft");
        sub.setBounceCount(sub.getBounceCount() + 1);
        sub.setLastBouncedAt(now);

        // Suppress if threshold reached — COALESCE: don't overwrite earlier suppression
        if (sub.getBounceCount() >= softBounceThreshold && sub.getSuppressedAt() == null) {
            sub.setSuppressedAt(now);
            log.info("Soft bounce threshold ({}) reached for {} — suppressing", softBounceThreshold, email);
        }
        subscriberRepository.save(sub);

        updateRecipientBounce(email, sendId, "soft", now);
    }

    private void handleComplaint(JsonNode sesNotification) {
        JsonNode complaint = sesNotification.path("complaint");
        JsonNode recipients = complaint.path("complainedRecipients");
        UUID sendId = extractSendId(sesNotification);

        for (JsonNode recipient : recipients) {
            String email = recipient.path("emailAddress").asText("");
            if (email.isBlank()) {
                continue;
            }

            handleComplaintForEmail(email, sendId);
        }
    }

    @Transactional
    void handleComplaintForEmail(String email, UUID sendId) {
        log.info("Complaint for {}", email);
        Optional<NewsletterSubscriber> optSub = subscriberRepository.findByEmail(email);
        if (optSub.isEmpty()) {
            log.warn("Complaint for unknown subscriber: {}", email);
            return;
        }

        NewsletterSubscriber sub = optSub.get();
        Instant now = Instant.now();

        sub.setBounceType("complaint");
        sub.setBounceCount(Math.max(sub.getBounceCount(), sub.getBounceCount() + 1));
        sub.setLastBouncedAt(now);
        // Suppress immediately — COALESCE: don't overwrite earlier suppression
        if (sub.getSuppressedAt() == null) {
            sub.setSuppressedAt(now);
        }
        subscriberRepository.save(sub);

        updateRecipientBounce(email, sendId, "complaint", now);
    }

    private void updateRecipientBounce(String email, UUID sendId, String bounceType, Instant bouncedAt) {
        if (sendId == null) {
            return;
        }

        try {
            recipientRepository.findById(
                    new ch.batbern.events.domain.NewsletterRecipientId(sendId, email)
            ).ifPresent(recipient -> {
                recipient.setBounceType(bounceType);
                recipient.setBouncedAt(bouncedAt);
                recipientRepository.save(recipient);
            });
        } catch (Exception e) {
            log.warn("Failed to update recipient bounce record for {} / send {}: {}",
                    email, sendId, e.getMessage());
        }
    }

    private UUID extractSendId(JsonNode sesNotification) {
        JsonNode tags = sesNotification.path("mail").path("tags").path("sendId");
        if (tags.isArray() && !tags.isEmpty()) {
            try {
                return UUID.fromString(tags.get(0).asText());
            } catch (IllegalArgumentException e) {
                log.debug("Invalid sendId tag: {}", tags.get(0).asText());
            }
        }
        return null;
    }
}
