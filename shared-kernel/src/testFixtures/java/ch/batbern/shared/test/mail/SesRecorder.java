package ch.batbern.shared.test.mail;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.MessageTag;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The test stand-in for Amazon SES (17.0b3): a {@link SesV2Client} that records every request and
 * sends nothing. It is the only {@code SesV2Client} a test context may hold, so the real
 * {@code EmailService} runs in every integration test, with every wall it has in production
 * (address checks, reserved domains, the test-marker wall), and a test asserts what SES would have
 * received: the parsed To:, Cc:, Reply-To:, subject, HTML body and attachment names.
 *
 * <p>Like SES API v2, it refuses a message over {@link #MAX_MESSAGE_BYTES} (40 MB) with
 * {@link #MESSAGE_TOO_LARGE}, so a test can show that a large mail goes out and a too-large one is
 * recorded as failed. (API v1 refused over 10 MB: a 10.9 MB forward failed in production 2026-11-22.)
 *
 * <p>Usage: the service's {@code TestAwsConfig} exposes one recorder as a bean and its
 * {@link #client()} as the {@code SesV2Client} bean. Tests autowire the recorder, {@link #reset()} it
 * in {@code @BeforeEach} and read {@link #mails()}. Never {@code verify(emailService)}: assert the
 * effect.
 *
 * <p>To simulate an SES refusal, {@link #failWith(Supplier)} makes every following send throw, and
 * {@link #failFor(String, Supplier)} every send to one address, until {@link #reset()}; use
 * {@link #sesError(String, String)} for the exception.
 *
 * <p>{@code EmailService} sends on the {@code @Async} executor. A recorder built with a drain
 * ({@code new SesRecorder(asyncTasks::awaitIdle)}, see {@code AsyncTaskTracker}) runs it at the start
 * of every {@link #reset()}, so a late send of the previous test is finished, then forgotten, and
 * never reaches the next test's assertions. A test that asserts a mail sent on the async path calls
 * {@link #awaitAsyncSends()} first.
 */
public final class SesRecorder {

    /** The SES API v2 maximum message size, 40 MB (SES quotas: "40 MB per message"). */
    public static final int MAX_MESSAGE_BYTES = 40 * 1024 * 1024;

    /**
     * Error code of the refusal of a message over {@link #MAX_MESSAGE_BYTES}. SES v2 answers that
     * request with HTTP 400; the exact code is not documented, so this is the recorder's own name.
     */
    public static final String MESSAGE_TOO_LARGE = "MessageTooLarge";

    private final SesV2Client client = Mockito.mock(SesV2Client.class);
    private final List<RecordedMail> mails = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile Supplier<? extends RuntimeException> failure;
    private final Map<String, Supplier<? extends RuntimeException>> failureByAddress = new ConcurrentHashMap<>();
    private volatile Consumer<RecordedMail> onEachSend = mail -> { };
    private final Runnable drain;

    /** A recorder without an async drain (unit tests that send synchronously). */
    public SesRecorder() {
        this(() -> { });
    }

    /**
     * @param drain blocks until the context's {@code @Async} work is done; run by {@link #reset()} and
     *              {@link #awaitAsyncSends()}
     */
    public SesRecorder(Runnable drain) {
        this.drain = drain;
        Mockito.when(client.sendEmail(Mockito.any(SendEmailRequest.class))).thenAnswer(invocation -> {
            SendEmailRequest request = invocation.getArgument(0);
            EmailContent content = request.content();
            if (content == null || (content.raw() == null) == (content.simple() == null)) {
                throw sesError("BadRequestException", "Content must hold exactly one of Simple or Raw");
            }
            if (messageBytes(content) > MAX_MESSAGE_BYTES) {
                throw sesError(MESSAGE_TOO_LARGE, "Message length is more than " + MAX_MESSAGE_BYTES + " bytes long");
            }
            String id = nextMessageId();
            RecordedMail mail = content.raw() != null ? fromRawContent(request, id) : fromSimpleContent(request, id);
            throwIfFailing(mail);
            mails.add(mail);
            onEachSend.accept(mail);
            return SendEmailResponse.builder().messageId(id).build();
        });
    }

    /** The {@code SesV2Client} to register as the context's only SES client. */
    public SesV2Client client() {
        return client;
    }

    /** Every mail SES received since the last {@link #reset()}, in send order. */
    public List<RecordedMail> mails() {
        return List.copyOf(mails);
    }

    /** The mails with {@code address} in To: or Cc: (case-insensitive). */
    public List<RecordedMail> mailsTo(String address) {
        return mails.stream().filter(mail -> mail.isAddressedTo(address)).toList();
    }

    /** The only recorded mail; fails when there is none or more than one. */
    public RecordedMail single() {
        List<RecordedMail> all = mails();
        if (all.size() != 1) {
            throw new AssertionError("expected exactly one mail sent to SES, got " + all.size() + ": "
                    + all.stream().map(m -> m.to() + " " + m.subject()).collect(Collectors.joining("; ")));
        }
        return all.get(0);
    }

    /** Every following send throws what {@code exception} supplies, until {@link #reset()}. */
    public void failWith(Supplier<? extends RuntimeException> exception) {
        this.failure = exception;
    }

    /** Every following send to {@code address} (envelope, case-insensitive) throws, until {@link #reset()}. */
    public void failFor(String address, Supplier<? extends RuntimeException> exception) {
        failureByAddress.put(address.toLowerCase(Locale.ROOT), exception);
    }

    /** An SES refusal as the SDK throws it, with error details (EmailService logs them). */
    public static SesV2Exception sesError(String errorCode, String message) {
        return (SesV2Exception) SesV2Exception.builder()
                .message(message)
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).errorMessage(message).build())
                .build();
    }

    /**
     * Runs {@code callback} on the sending thread right after each mail is recorded, e.g. to let a
     * recipient act while a send loop is still running. Cleared by {@link #reset()}.
     */
    public void onEachSend(Consumer<RecordedMail> callback) {
        this.onEachSend = callback;
    }

    /** Waits for the sends still running on the {@code @Async} executor; keeps what was recorded. */
    public void awaitAsyncSends() {
        drain.run();
    }

    /**
     * Waits for the async sends still running (they belong to the previous test), then forgets the
     * recorded mails, every {@link #failWith} / {@link #failFor} failure and the callback.
     */
    public void reset() {
        drain.run();
        mails.clear();
        failure = null;
        failureByAddress.clear();
        onEachSend = mail -> { };
    }

    private void throwIfFailing(RecordedMail mail) {
        Supplier<? extends RuntimeException> current = failure;
        if (current != null) {
            throw current.get();
        }
        for (String address : mail.envelope()) {
            Supplier<? extends RuntimeException> forAddress = failureByAddress.get(address.toLowerCase(Locale.ROOT));
            if (forAddress != null) {
                throw forAddress.get();
            }
        }
    }

    private String nextMessageId() {
        return "ses-recorder-" + sequence.incrementAndGet();
    }

    /** Size of the message as SES measures it: the raw MIME, or subject plus bodies of simple content. */
    private static long messageBytes(EmailContent content) {
        if (content.raw() != null) {
            return content.raw().data() != null ? content.raw().data().asByteArrayUnsafe().length : 0;
        }
        software.amazon.awssdk.services.sesv2.model.Message message = content.simple();
        Body body = message.body();
        return utf8Length(message.subject()) + (body != null ? utf8Length(body.html()) + utf8Length(body.text()) : 0);
    }

    private static long utf8Length(Content content) {
        return content != null && content.data() != null ? content.data().getBytes(StandardCharsets.UTF_8).length : 0;
    }

    private static RecordedMail fromSimpleContent(SendEmailRequest request, String messageId) {
        Destination destination = request.destination();
        List<String> to = destination != null ? List.copyOf(destination.toAddresses()) : List.of();
        List<String> cc = destination != null ? List.copyOf(destination.ccAddresses()) : List.of();
        List<String> envelope = new ArrayList<>(to);
        envelope.addAll(cc);
        if (destination != null) {
            envelope.addAll(destination.bccAddresses());
        }
        software.amazon.awssdk.services.sesv2.model.Message message = request.content().simple();
        Body body = message.body();
        InternetAddress source = parseAddress(request.fromEmailAddress());
        return new RecordedMail(messageId, false, to, cc, List.copyOf(request.replyToAddresses()),
                source != null ? source.getAddress() : request.fromEmailAddress(),
                source != null ? source.getPersonal() : null, data(message.subject()),
                data(body != null ? body.html() : null), data(body != null ? body.text() : null), List.of(),
                List.copyOf(envelope), request.configurationSetName(), tags(request.emailTags()), Map.of());
    }

    private static RecordedMail fromRawContent(SendEmailRequest request, String messageId) {
        byte[] raw = request.content().raw().data().asByteArray();
        try {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            List<String> to = addresses(mime.getRecipients(Message.RecipientType.TO));
            List<String> cc = addresses(mime.getRecipients(Message.RecipientType.CC));
            List<String> destinations = destinationAddresses(request.destination());
            List<String> envelope = destinations.isEmpty() ? concat(to, cc) : destinations;
            Parts parts = new Parts();
            collect(mime, parts);
            Address[] from = mime.getFrom();
            InternetAddress sender = from != null && from.length > 0 && from[0] instanceof InternetAddress ia
                    ? ia : null;
            String[] replyTo = mime.getHeader("Reply-To");
            Map<String, String> headers = new LinkedHashMap<>();
            for (Enumeration<Header> all = mime.getAllHeaders(); all.hasMoreElements(); ) {
                Header header = all.nextElement();
                headers.putIfAbsent(header.getName(), MimeUtility.unfold(header.getValue()));
            }
            return new RecordedMail(messageId, true, to, cc,
                    replyTo != null ? addresses(mime.getReplyTo()) : List.of(),
                    sender != null ? sender.getAddress() : null, sender != null ? sender.getPersonal() : null,
                    mime.getSubject(), parts.html, parts.text, List.copyOf(parts.attachments), envelope,
                    request.configurationSetName(), tags(request.emailTags()), Map.copyOf(headers));
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("SesRecorder could not parse the raw MIME message", e);
        }
    }

    /** Every address of a raw send's {@code Destination} (the envelope), empty when it has none. */
    private static List<String> destinationAddresses(Destination destination) {
        if (destination == null) {
            return List.of();
        }
        List<String> all = new ArrayList<>(destination.toAddresses());
        all.addAll(destination.ccAddresses());
        all.addAll(destination.bccAddresses());
        return List.copyOf(all);
    }

    private static void collect(Part part, Parts parts) throws MessagingException, IOException {
        String filename = part.getFileName();
        if (filename != null) {
            parts.attachments.add(filename);
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, parts);
            }
        } else if (part.isMimeType("text/html") && parts.html == null) {
            parts.html = String.valueOf(part.getContent());
        } else if (part.isMimeType("text/plain") && parts.text == null) {
            parts.text = String.valueOf(part.getContent());
        }
    }

    private static final class Parts {
        private String html;
        private String text;
        private final List<String> attachments = new ArrayList<>();
    }

    private static List<String> addresses(Address[] addresses) {
        if (addresses == null) {
            return List.of();
        }
        return Arrays.stream(addresses)
                .map(a -> a instanceof InternetAddress ia ? ia.getAddress() : a.toString())
                .toList();
    }

    private static InternetAddress parseAddress(String address) {
        if (address == null) {
            return null;
        }
        try {
            return new InternetAddress(address);
        } catch (MessagingException e) {
            return null;
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    private static String data(Content content) {
        return content != null ? content.data() : null;
    }

    private static Map<String, String> tags(List<MessageTag> tags) {
        return tags.stream().collect(Collectors.toUnmodifiableMap(MessageTag::name, MessageTag::value));
    }

    /**
     * One request as SES received it.
     *
     * @param raw      sent as raw content (MIME parsed) rather than simple content of {@code SendEmail}
     * @param to       the To: addresses, one per entry
     * @param replyTo  the Reply-To: addresses (empty when the mail has none)
     * @param from     the From: address; {@code fromName} its display name
     * @param envelope the addresses SES delivers to (for a raw send, its {@code Destination})
     * @param tags     message tags, e.g. {@code mail_group}
     * @param headers  every MIME header of a raw send, first value, unfolded (empty for simple content)
     */
    public record RecordedMail(
            String sesMessageId,
            boolean raw,
            List<String> to,
            List<String> cc,
            List<String> replyTo,
            String from,
            String fromName,
            String subject,
            String html,
            String text,
            List<String> attachmentNames,
            List<String> envelope,
            String configurationSetName,
            Map<String, String> tags,
            Map<String, String> headers) {

        /** The MIME {@code Message-ID} of a raw send, else null. */
        public String messageIdHeader() {
            return headers.get("Message-ID");
        }

        /** True when {@code address} is in To: or Cc: (case-insensitive). */
        public boolean isAddressedTo(String address) {
            String wanted = address.toLowerCase(Locale.ROOT);
            return to.stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(wanted))
                    || cc.stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(wanted));
        }
    }
}
