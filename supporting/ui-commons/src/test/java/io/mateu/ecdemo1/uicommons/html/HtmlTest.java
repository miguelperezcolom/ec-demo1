package io.mateu.ecdemo1.uicommons.html;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTest {

    @Test
    void data_is_escaped_including_interpolation() {
        assertThat(Html.escape("<b>&\"'${x}")).isEqualTo("&lt;b&gt;&amp;&quot;&#39;&#36;{x}");
        assertThat(Html.escape(null)).isEmpty();
    }

    @Test
    void external_links_open_in_a_new_tab_and_no_href_is_text() {
        assertThat(Html.link("Case", "https://sf/x")).isEqualTo("<a href=\"https://sf/x\" target=\"_blank\" rel=\"noopener\">Case</a>");
        assertThat(Html.link("Booking", "/booking/1")).isEqualTo("<a href=\"/booking/1\">Booking</a>");
        assertThat(Html.link("Profile 9", null)).isEqualTo("Profile 9");
    }

    @Test
    void the_links_table_has_a_line_per_link() {
        var html = Html.linksTable(List.of(new Html.Link("Opera · reservation", "98765", null),
                new Html.Link("Holder · customer", "Ana", "/customers/1")));
        assertThat(html).startsWith("<table").endsWith("</table>")
                .contains(">Opera · reservation</th>", ">98765</td>", "<a href=\"/customers/1\">Ana</a>");
    }
}
