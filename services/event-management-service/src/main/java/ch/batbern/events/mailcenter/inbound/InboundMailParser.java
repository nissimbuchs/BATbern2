package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.MailMessage;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a raw inbound mail into an {@link InboundMail} (Story 16.2a). Tolerant: a header it cannot
 * read is left null rather than failing the mail, because a person may still have written it.
 *
 * <p>HTML is stored only for mails without a text part, and only after sanitizing: no images, no
 * styles, no scripts, no event handlers. Nothing remote may load in the organizer's browser
 * (tracking pixels), and the page shows the body in a {@code sandbox=""} frame on top.
 */
@Slf4j
public final class InboundMailParser {

    private static final Pattern ANGLE_ID = Pattern.compile("<([^<>\\s]+)>");

    /** Relaxed formatting, links kept (as text + href), but nothing that loads or runs. */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .removeTags("img")
            .removeAttributes(":all", "style")
            .addProtocols("a", "href", "http", "https", "mailto");

    /** RFC 6532: some clients send raw UTF-8 headers; read them as UTF-8, not Latin-1. */
    private static final Session SESSION;

    static {
        Properties properties = new Properties();
        properties.setProperty("mail.mime.allowutf8", "true");
        SESSION = Session.getInstance(properties);
    }

    private InboundMailParser() {
    }

    public static InboundMail parse(byte[] raw) {
        MimeMessage message;
        try {
            message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
        } catch (MessagingException e) {
            throw new IllegalArgumentException("Unparseable mail: " + e.getMessage(), e);
        }
        Body body = new Body();
        try {
            walk(message, body);
        } catch (MessagingException | IOException e) {
            log.warn("Inbound mail: could not read every part: {}", e.getMessage());
        }
        String text = body.text != null ? body.text
                : body.html != null ? Jsoup.parse(body.html).wholeText().strip() : "";
        String html = body.text == null && body.html != null ? sanitize(body.html) : null;

        InternetAddress from = first(header(message, "From"));
        InternetAddress replyTo = first(header(message, "Reply-To"));
        return new InboundMail(
                firstId(header(message, "Message-ID")),
                firstId(header(message, "In-Reply-To")),
                ids(header(message, "References")),
                from != null ? lower(from.getAddress()) : null,
                from != null ? blankToNull(from.getPersonal()) : null,
                replyTo != null ? lower(replyTo.getAddress()) : null,
                addresses(header(message, "To")),
                addresses(header(message, "Cc")),
                decode(header(message, "Subject")),
                text,
                html,
                body.attachments,
                header(message, "Content-Type"),
                header(message, "Return-Path"),
                header(message, "Auto-Submitted"),
                header(message, "Precedence"),
                header(message, "X-Autoreply"),
                header(message, "X-Autorespond"),
                header(message, "X-BATbern-Forwarded") != null);
    }

    /**
     * Story 16.6: the bytes of each attachment, in the order of {@link InboundMail#attachments()}.
     * Kept apart from {@link #parse} so the parsed mail never holds file content.
     */
    public static List<byte[]> attachmentContents(byte[] raw) {
        List<byte[]> contents = new ArrayList<>();
        try {
            collect(new MimeMessage(SESSION, new ByteArrayInputStream(raw)), contents);
        } catch (MessagingException | IOException e) {
            log.warn("Inbound mail: could not read every attachment: {}", e.getMessage());
        }
        return contents;
    }

    private static void collect(Part part, List<byte[]> contents) throws MessagingException, IOException {
        if (isAttachment(part)) {
            try (InputStream in = part.getInputStream()) {
                contents.add(in.readAllBytes());
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), contents);
            }
        }
    }

    private static boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || (fileName(part) != null && !part.isMimeType("multipart/*"));
    }

    /** Sanitized HTML: what {@link #parse} stores for an HTML-only mail. */
    public static String sanitize(String html) {
        return Jsoup.clean(html, SAFELIST);
    }

    private static final class Body {
        private String text;
        private String html;
        private final List<MailMessage.Attachment> attachments = new ArrayList<>();
    }

    private static void walk(Part part, Body body) throws MessagingException, IOException {
        String fileName = fileName(part);
        if (isAttachment(part)) {
            body.attachments.add(new MailMessage.Attachment(
                    fileName != null ? fileName : "attachment",
                    baseType(part.getContentType()),
                    size(part)));
            return;
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                walk(multipart.getBodyPart(i), body);
            }
            return;
        }
        if (part.isMimeType("text/plain") && body.text == null) {
            body.text = String.valueOf(part.getContent()).strip();
        } else if (part.isMimeType("text/html") && body.html == null) {
            body.html = String.valueOf(part.getContent());
        }
    }

    private static String fileName(Part part) {
        try {
            String name = part.getFileName();
            return name == null ? null : MimeUtility.decodeText(name);
        } catch (MessagingException | UnsupportedEncodingException e) {
            return null;
        }
    }

    private static long size(Part part) throws MessagingException, IOException {
        try (InputStream in = part.getInputStream()) {
            return in.readAllBytes().length;
        }
    }

    private static String baseType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    }

    private static String header(MimeMessage message, String name) {
        try {
            String value = message.getHeader(name, ",");
            return value == null ? null : value.replaceAll("\\r?\\n[ \\t]+", " ").trim();
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return MimeUtility.decodeText(value);
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static String firstId(String header) {
        List<String> ids = ids(header);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static List<String> ids(String header) {
        List<String> ids = new ArrayList<>();
        if (header == null) {
            return ids;
        }
        Matcher m = ANGLE_ID.matcher(header);
        while (m.find()) {
            ids.add(m.group(1));
        }
        if (ids.isEmpty() && !header.isBlank() && !header.trim().contains(" ")) {
            ids.add(header.trim());
        }
        return ids;
    }

    private static InternetAddress first(String header) {
        List<InternetAddress> parsed = parseAddresses(header);
        return parsed.isEmpty() ? null : parsed.get(0);
    }

    private static List<String> addresses(String header) {
        return parseAddresses(header).stream().map(a -> lower(a.getAddress())).filter(a -> a != null).toList();
    }

    private static List<InternetAddress> parseAddresses(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        try {
            List<InternetAddress> result = new ArrayList<>();
            for (Address address : InternetAddress.parseHeader(header, false)) {
                if (address instanceof InternetAddress internet && internet.getAddress() != null) {
                    result.add(internet);
                }
            }
            return result;
        } catch (AddressException e) {
            return List.of();
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
