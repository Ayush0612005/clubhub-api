package com.clubhub.campus;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads SRM's WordPress events RSS feed (www.srmist.edu.in/events/feed/). The feed has no event-date
 * field, only the post date, so the event's dates are pulled out of the text ("on 25 September 2026",
 * "from 24-26 February 2027"). Best effort: an admin reviews every imported item before students see it.
 */
final class SrmFeedParser {

    record FeedItem(String guid, String title, String link, String text, LocalDate startDate, LocalDate endDate) {
    }

    record DateRange(LocalDate start, LocalDate end) {
    }

    private static final String MONTH = "\\b(January|February|March|April|May|June|July|August|September|October|"
            + "November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)\\.?";
    // \b: never take the "26" out of "2026"
    private static final String DAY = "\\b(\\d{1,2})(?:st|nd|rd|th)?";
    private static final String SEP = "\\s*(?:-|–|—|to|and|&)\\s*";

    // tried in this order; the match that appears first in the text wins
    private static final Pattern RANGE_SAME_MONTH = Pattern.compile(DAY + SEP + DAY + "\\s+" + MONTH + ",?\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_TWO_MONTHS = Pattern.compile(DAY + "\\s+" + MONTH + SEP + DAY + "\\s+" + MONTH
            + ",?\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE = Pattern.compile(DAY + "\\s+" + MONTH + ",?\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_FIRST = Pattern.compile(MONTH + "\\s+" + DAY + ",?\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private SrmFeedParser() {
    }

    static List<FeedItem> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // untrusted XML from the internet: no DTDs, no external entities (XXE)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            // strip() and a stray BOM: a pasted feed often starts with whitespace, which XML forbids before <?xml
            String cleaned = xml.replaceFirst("^\\x{FEFF}", "").strip();
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(cleaned)));

            NodeList items = doc.getElementsByTagName("item");
            List<FeedItem> result = new ArrayList<>(items.getLength());
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String link = text(item, "link");
                String guid = text(item, "guid");
                String title = plain(text(item, "title"));
                String body = withoutWordPressBoilerplate(plain(text(item, "description")));
                if (title.isBlank() || link.isBlank()) {
                    continue;
                }
                DateRange dates = extractDates(body);
                result.add(new FeedItem(guid.isBlank() ? link : guid, title, link, body,
                        dates == null ? null : dates.start(), dates == null ? null : dates.end()));
            }
            return result;
        } catch (Exception e) {
            // 400, not 500: most often a partial copy-paste of the feed
            throw new IllegalArgumentException("That isn't the SRM events feed (not valid RSS). Copy the whole page from "
                    + "view-source:https://www.srmist.edu.in/events/feed/ and paste it again.", e);
        }
    }

    /** The first date or date range mentioned in the text, or null if there is none. */
    static DateRange extractDates(String text) {
        if (text == null) {
            return null;
        }
        DateRange best = null;
        int bestAt = Integer.MAX_VALUE;
        for (Pattern p : List.of(RANGE_SAME_MONTH, RANGE_TWO_MONTHS, SINGLE, MONTH_FIRST)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                DateRange range = toRange(p, m);
                if (range != null) {
                    if (m.start() < bestAt) {
                        best = range;
                        bestAt = m.start();
                    }
                    break; // only this pattern's first valid match can be the earliest
                }
            }
        }
        return best;
    }

    private static DateRange toRange(Pattern p, Matcher m) {
        try {
            if (p == RANGE_SAME_MONTH) {
                int year = Integer.parseInt(m.group(4));
                Month month = month(m.group(3));
                return ordered(LocalDate.of(year, month, Integer.parseInt(m.group(1))),
                        LocalDate.of(year, month, Integer.parseInt(m.group(2))));
            }
            if (p == RANGE_TWO_MONTHS) {
                int year = Integer.parseInt(m.group(5));
                LocalDate start = LocalDate.of(year, month(m.group(2)), Integer.parseInt(m.group(1)));
                LocalDate end = LocalDate.of(year, month(m.group(4)), Integer.parseInt(m.group(3)));
                // "28 December - 2 January 2027": the start belongs to the previous year
                return end.isBefore(start) ? new DateRange(start.minusYears(1), end) : new DateRange(start, end);
            }
            if (p == SINGLE) {
                LocalDate day = LocalDate.of(Integer.parseInt(m.group(3)), month(m.group(2)), Integer.parseInt(m.group(1)));
                return new DateRange(day, day);
            }
            LocalDate day = LocalDate.of(Integer.parseInt(m.group(3)), month(m.group(1)), Integer.parseInt(m.group(2)));
            return new DateRange(day, day);
        } catch (RuntimeException invalidDate) { // e.g. "31 February"
            return null;
        }
    }

    private static DateRange ordered(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? new DateRange(b, a) : new DateRange(a, b);
    }

    private static Month month(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace(".", "").substring(0, 3);
        for (Month m : Month.values()) {
            if (m.name().toLowerCase(Locale.ROOT).startsWith(key)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown month " + name);
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static final Pattern SLIDER_BUTTONS = Pattern.compile("^(?:Previous\\s+Next\\s+)+");
    private static final Pattern POST_FOOTER = Pattern.compile("\\s*The post .{1,400}? first appeared on .{1,80}$");

    /** WordPress wraps excerpts in a slider's "Previous Next" labels and a "The post X first appeared on Y." footer. */
    static String withoutWordPressBoilerplate(String text) {
        String s = SLIDER_BUTTONS.matcher(text).replaceFirst("");
        return POST_FOOTER.matcher(s).replaceFirst("").replace("[…]", "…").trim();
    }

    /** Strip HTML tags and decode the few entities WordPress emits, collapsing whitespace. */
    static String plain(String html) {
        String s = html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#8217;", "’").replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”")
                .replace("&#8211;", "–").replace("&#8212;", "—").replace("&#038;", "&").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("[&hellip;]", "…").replace("&hellip;", "…");
        return s.replaceAll("\\s+", " ").trim();
    }
}
