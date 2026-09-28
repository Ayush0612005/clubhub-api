package com.clubhub.campus;

import com.clubhub.campus.SrmFeedParser.DateRange;
import com.clubhub.campus.SrmFeedParser.FeedItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SrmFeedParserTest {

    @Test
    void readsDatesTheWaySrmWritesThem() {
        assertThat(SrmFeedParser.extractDates("an outreach programme on 25 September 2026 at Athur Village"))
                .isEqualTo(new DateRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25)));
        assertThat(SrmFeedParser.extractDates("will be organized from 24-26 February 2027 by the Centre"))
                .isEqualTo(new DateRange(LocalDate.of(2027, 2, 24), LocalDate.of(2027, 2, 26)));
        assertThat(SrmFeedParser.extractDates("Conference on 20–21 August 2026, bringing together experts"))
                .isEqualTo(new DateRange(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21)));
        assertThat(SrmFeedParser.extractDates("runs from 30 October to 2 November 2026"))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)));
        assertThat(SrmFeedParser.extractDates("Deadline: Oct 5, 2026. Register early"))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)));
        assertThat(SrmFeedParser.extractDates("the 3rd of the month")).isNull();
    }

    @Test
    void takesTheFirstDateMentioned() {
        assertThat(SrmFeedParser.extractDates("Held on 12 March 2027. Registrations close on 1 March 2027."))
                .isEqualTo(new DateRange(LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 12)));
    }

    @Test
    void ignoresImpossibleDates() {
        assertThat(SrmFeedParser.extractDates("on 31 February 2027, and again on 3 March 2027"))
                .isEqualTo(new DateRange(LocalDate.of(2027, 3, 3), LocalDate.of(2027, 3, 3)));
    }

    @Test
    void parsesRssItemsAndStripsHtml() {
        List<FeedItem> items = SrmFeedParser.parse(feed("""
                <item>
                  <title>ICRCAM-2027</title>
                  <link>https://www.srmist.edu.in/events/icrcam-2027/</link>
                  <guid isPermaLink="false">https://www.srmist.edu.in/?post_type=events&amp;p=1</guid>
                  <description><![CDATA[<p>The 4th International Conference will be organized from 24-26 February 2027 by CCAM &amp; SRMIST&#8217;s School of Mechanical Engineering.</p>]]></description>
                </item>"""));

        assertThat(items).hasSize(1);
        FeedItem item = items.getFirst();
        assertThat(item.title()).isEqualTo("ICRCAM-2027");
        assertThat(item.guid()).isEqualTo("https://www.srmist.edu.in/?post_type=events&p=1");
        assertThat(item.text()).startsWith("The 4th International Conference").contains("CCAM & SRMIST’s").doesNotContain("<p>");
        assertThat(item.startDate()).isEqualTo(LocalDate.of(2027, 2, 24));
        assertThat(item.endDate()).isEqualTo(LocalDate.of(2027, 2, 26));
    }

    @Test
    void dropsWordPressSliderLabelsAndPostFooter() {
        // real shape of an srmist.edu.in excerpt
        assertThat(SrmFeedParser.withoutWordPressBoilerplate(
                "Previous Next KONNECT 2026 brings together three days of challenges […] "
                        + "The post KONNECT 2026 first appeared on SRMIST ."))
                .isEqualTo("KONNECT 2026 brings together three days of challenges …");
        assertThat(SrmFeedParser.withoutWordPressBoilerplate("A plain description.")).isEqualTo("A plain description.");
    }

    @Test
    void refusesXmlWithADoctype() {
        // XXE guard: a DOCTYPE could pull in local files or remote URLs
        String evil = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&xxe;</title><link>x</link></item></channel></rss>""";
        assertThatThrownBy(() -> SrmFeedParser.parse(evil)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAPasteWithSurroundingWhitespaceAndRejectsJunk() {
        String pasted = "\n   " + feed("<item><title>T</title><link>https://www.srmist.edu.in/events/t/</link></item>") + "\n";
        assertThat(SrmFeedParser.parse(pasted)).hasSize(1);
        assertThatThrownBy(() -> SrmFeedParser.parse("This XML file does not appear to have any style information"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("view-source:");
    }

    static String feed(String items) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Events - SRMIST</title>
                %s
                </channel></rss>""".formatted(items);
    }
}
