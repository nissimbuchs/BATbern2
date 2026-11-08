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
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
 * The test stand-in for Amazon SES (17.0b3): a {@link SesClient} that records every request and
 * sends nothing. It is the only {@code SesClient} a test context may hold, so the real
 * {@code EmailService} runs in every integration test, with every wall it has in production
 * (address checks, reserved domains, the test-marker wall), and a test asserts what SES would have
 * received: the parsed To:, Cc:, Reply-To:, subject, HTML body and attachment names.
 *
 * <p>Usage: the service's {@code TestAwsConfig} exposes one recorder as a bean and its
 * {@link #client()} as the {@code SesClient} bean. Tests autowire the recorder, {@link #reset()} it
 * in {@code @BeforeEach} and read {@link #mails()}. Never {@code verify(emailService)}: assert the
 * effect.
 *
 * <p>To simulate an SES refusal, {@link #failWith(Supplier)} makes every following send throw, and
 * {@link #failFor(String, Supplier)} every send to one address, until {@link #reset()}; use
 * {@link #sesError(String, String)} for the exception.
 */
public final class SesRecorder {

    private final SesClient client = Mockito.mock(SesClient.class);
    private final List<RecordedMail> mails = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile Supplier<? extends RuntimeException> failure;
    private final Map<String, Supplier<? extends RuntimeException>> failureByAddress = new ConcurrentHashMap<>();
    private volatile Consumer<RecordedMail> onEachSend = mail -> { };

    public SesRecorder() {
        Mockito.when(client.sendEmail(Mockito.any(SendEmailRequest.class))).thenAnswer(invocation -> {
            SendEmailRequest request = invocation.getArgument(0);
            String id = nextMessageId();
            RecordedMail mail = fromSendEmail(request, id);
            throwIfFailing(mail);
            mails.add(mail);
            onEachSend.accept(mail);
            return SendEmailResponse.builder().messageId(id).build();
        });
        Mockito.when(client.sendRawEmail(Mockito.any(SendRawEmailRequest.class))).thenAnswer(invocation -> {
            SendRawEmailRequest request = invocation.getArgument(0);
            String id = nextMessageId();
            RecordedMail mail = fromSendRawEmail(request, id);
            throwIfFailing(mail);
            mails.add(mail);
            onEachSend.accept(mail);
            return SendRawEmailResponse.builder().messageId(id).build();
        });
    }

    /** The {@code SesClient} to register as the context's only SES client. */
    public SesClient client() {
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
    public static SesException sesError(String errorCode, String message) {
        return (SesException) SesException.builder()
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

    /** Forget the recorded mails, every {@link #failWith} / {@link #failFor} failure and the callback. */
    public void reset() {
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

    private static RecordedMail fromSendEmail(SendEmailRequest request, String messageId) {
        Destination destination = request.destination();
        List<String> to = destination != null ? List.copyOf(destination.toAddresses()) : List.of();
        List<String> cc = destination != null ? List.copyOf(destination.ccAddresses()) : List.of();
        List<String> envelope = new ArrayList<>(to);
        envelope.addAll(cc);
        if (destination != null) {
            envelope.addAll(destination.bccAddresses());
        }
        software.amazon.awssdk.services.ses.model.Message message = request.message();
        Body body = message != null ? message.body() : null;
        InternetAddress source = parseAddress(request.source());
        return new RecordedMail(messageId, false, to, cc, List.copyOf(request.replyToAddresses()),
                source != null ? source.getAddress() : request.source(), source != null ? source.getPersonal() : null,
                data(message != null ? message.subject() : null),
                data(body != null ? body.html() : null), data(body != null ? body.text() : null), List.of(),
                List.copyOf(envelope), request.configurationSetName(), tags(request.tags()), Map.of());
    }

    private static RecordedMail fromSendRawEmail(SendRawEmailRequest request, String messageId) {
        byte[] raw = request.rawMessage().data().asByteArray();
        try {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            List<String> to = addresses(mime.getRecipients(Message.RecipientType.TO));
            List<String> cc = addresses(mime.getRecipients(Message.RecipientType.CC));
            List<String> envelope = request.destinations().isEmpty() ? concat(to, cc)
                    : List.copyOf(request.destinations());
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
                    request.configurationSetName(), tags(request.tags()), Map.copyOf(headers));
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("SesRecorder could not parse the raw MIME message", e);
        }
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
     * @param raw      sent through {@code SendRawEmail} (MIME parsed) rather than {@code SendEmail}
     * @param to       the To: addresses, one per entry
     * @param replyTo  the Reply-To: addresses (empty when the mail has none)
     * @param from     the From: address; {@code fromName} its display name
     * @param envelope the addresses SES delivers to (for a raw send, its {@code destinations})
     * @param tags     message tags, e.g. {@code mail_group}
     * @param headers  every MIME header of a raw send, first value, unfolded (empty for SendEmail)
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
