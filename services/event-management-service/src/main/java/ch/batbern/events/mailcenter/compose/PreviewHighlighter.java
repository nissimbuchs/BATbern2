package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story 16.5 AC1/AC2: the own-text preview with every filled value marked, for display only. It
 * renders the same content a second time with each non-empty value wrapped in control-character
 * sentinels, then turns a wrapped value outside a tag into {@code <mark data-var="name">} and strips
 * the sentinels inside a tag (an {@code href}), so the HTML stays valid. A placeholder left over
 * (unknown) becomes {@code <mark class="unknown">}. Empty values are not wrapped, so conditional
 * blocks render as in the real mail. What is compared and sent is the unhighlighted render.
 */
public final class PreviewHighlighter {

    private static final char START = '\u0001';
    private static final char NAME_END = '\u0002';
    private static final char END = '\u0003';
    private static final Pattern WRAPPED = Pattern.compile(START + "([^" + NAME_END + "]*)" + NAME_END
            + "([^" + END + "]*)" + END);
    private static final Pattern LEFTOVER = Pattern.compile("\\{\\{[#/^]?[^{}]*}}");
    static final String STYLE = "<style>mark[data-var]{background:#fff3b0;}"
            + "mark.unknown{background:#ffcdd2;color:#b71c1c;}</style>";

    private PreviewHighlighter() {
    }

    /** Subject and body with values marked; the body carries the mark styles. */
    public static RenderedMail highlight(MailPipeline pipeline, MailContent content, Map<String, String> variables) {
        Map<String, String> wrapped = new HashMap<>();
        variables.forEach((name, value) -> wrapped.put(name,
                value == null || value.isEmpty() ? value : START + name + NAME_END + value + END));
        RenderedMail rendered = pipeline.render(content, wrapped);
        String subject = rendered.subject() == null ? null : markText(escape(rendered.subject()));
        return new RenderedMail(subject, withStyle(markHtml(rendered.html())));
    }

    /** Outside tags: mark values and leftovers; inside tags: keep the value, drop the sentinels. */
    static String markHtml(String html) {
        StringBuilder out = new StringBuilder(html.length() + 64);
        boolean inTag = false;
        int i = 0;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            }
            if (c == START) {
                int nameEnd = html.indexOf(NAME_END, i);
                int end = html.indexOf(END, nameEnd + 1);
                if (nameEnd < 0 || end < 0) {
                    i++;
                    continue;
                }
                String name = html.substring(i + 1, nameEnd);
                String value = html.substring(nameEnd + 1, end);
                if (inTag) {
                    out.append(value);
                } else {
                    out.append("<mark data-var=\"").append(name).append("\">").append(value).append("</mark>");
                }
                i = end + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        return markLeftovers(out.toString());
    }

    /** Leftover placeholders outside tags become red marks. */
    private static String markLeftovers(String html) {
        StringBuilder out = new StringBuilder(html.length());
        Matcher matcher = LEFTOVER.matcher(html);
        int last = 0;
        while (matcher.find()) {
            out.append(html, last, matcher.start());
            boolean inTag = html.lastIndexOf('<', matcher.start()) > html.lastIndexOf('>', matcher.start());
            out.append(inTag ? matcher.group()
                    : "<mark class=\"unknown\">" + matcher.group() + "</mark>");
            last = matcher.end();
        }
        return out.append(html.substring(last)).toString();
    }

    private static String markText(String text) {
        Matcher matcher = WRAPPED.matcher(text);
        String marked = matcher.replaceAll(m -> Matcher.quoteReplacement(
                "<mark data-var=\"" + m.group(1) + "\">" + m.group(2) + "</mark>"));
        return markLeftovers(marked);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String withStyle(String html) {
        int head = html.indexOf("</head>");
        return head >= 0 ? html.substring(0, head) + STYLE + html.substring(head) : STYLE + html;
    }
}
