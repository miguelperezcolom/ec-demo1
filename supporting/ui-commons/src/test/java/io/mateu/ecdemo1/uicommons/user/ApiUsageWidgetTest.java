package io.mateu.ecdemo1.uicommons.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.MetricTrend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The welcome pages' tiles: integrations-service's cards as it sent them, clicking through to the page. */
class ApiUsageWidgetTest {

    @Test
    void theCardsAreTheOnesIntegrationsServiceSentAndOpenThePage() throws Exception {
        var sent = List.of(
                MetricCard.builder().id("salesforce").title("SALESFORCE — LLAMADAS LIBRES").value("1.007").unit("de 15.000")
                        .trend(MetricTrend.up).trendLabel("+675 en la última hora").icon("vaadin:cloud").description("visto 13:18").build(),
                MetricCard.builder().id("salesforce-pace").title("NUESTRAS").value("4").trend(MetricTrend.neutral).build(),
                MetricCard.builder().id("opera").title("OPERA").value("62").build());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(sent));

        var cards = ApiUsageWidget.cards(json, "verApisExternas");

        assertThat(cards).extracting(MetricCard::id).containsExactly("salesforce", "salesforce-pace", "opera");
        assertThat(cards.getFirst()).isEqualTo(MetricCard.builder().id("salesforce").title("SALESFORCE — LLAMADAS LIBRES")
                .value("1.007").unit("de 15.000").trend(MetricTrend.up).trendLabel("+675 en la última hora")
                .icon("vaadin:cloud").description("visto 13:18").actionId("verApisExternas").build());
        assertThat(cards).extracting(MetricCard::actionId).containsOnly("verApisExternas");
    }

    @Test
    void whenIntegrationsServiceDoesNotAnswerTheThreeTilesSaySo() {
        var cards = ApiUsageWidget.unavailable("go");
        assertThat(cards).extracting(MetricCard::id).containsExactly("salesforce", "salesforce-pace", "opera");
        assertThat(cards).extracting(MetricCard::value).containsOnly("—");
        assertThat(ApiUsageWidget.cards(null, "go")).isEmpty();
        assertThat(ApiUsageWidget.trend("sideways")).isNull();
    }
}
