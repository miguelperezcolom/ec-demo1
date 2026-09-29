package io.mateu.ecdemo1.mdm.salesforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.mdm.config.MdmConfig;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.store.ApiCallHourRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Every call to Salesforce counted under what it was for, the org's total read from the answers, and
 * the header's figures made of both: how much the org spent, how much of it was us, and on what.
 */
class SalesforceUsageTest {

    static final String ORG = "https://acme.my.salesforce.com";
    static final String TOKEN = """
            {"access_token":"t","instance_url":"https://acme.my.salesforce.com","id":"https://login/id/00D000000000001/005000000000001"}""";

    final SalesforceBudgetTest.Hands clock = new SalesforceBudgetTest.Hands();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final SalesforceBudget budget = new SalesforceBudget(clock, Duration.ofMinutes(5), Duration.ofMinutes(60));
    final ApiCalls calls = new MdmConfig().salesforceCalls(clock, registry);
    final MdmProperties properties = new MdmProperties(
            new MdmProperties.Salesforce("acme.my.salesforce.com", "id", "secret", "v67.0", null, 0, false),
            Duration.ofSeconds(5), Duration.ofMinutes(15));
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final SalesforceClient salesforce = new SalesforceClient(properties, new TolerantReader(new ObjectMapper()), budget,
            calls, builder);
    final ApiCallHourRepository nothingSaved = (ApiCallHourRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ApiCallHourRepository.class}, (proxy, m, args) -> List.of());
    final SalesforceUsage usage = new SalesforceUsage(salesforce, nothingSaved, properties, clock, registry, java.time.Duration.ofMinutes(15));

    void token() {
        server.expect(requestTo(ORG + "/services/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
    }

    static HttpHeaders limit(String used) {
        var headers = new HttpHeaders();
        headers.add("Sforce-Limit-Info", "api-usage=" + used + "/15000");
        return headers;
    }

    @Test
    void eachCallIsCountedUnderItsPurposeAndTheOrgsTotalIsReadFromTheAnswer() {
        token();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(ORG + "/services/data/v67.0/queryAll")))
                .andRespond(withSuccess("{\"records\":[],\"done\":true}", MediaType.APPLICATION_JSON).headers(limit("14000")));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(ORG + "/services/data/v67.0/queryAll")))
                .andRespond(withSuccess("{\"records\":[],\"done\":true}", MediaType.APPLICATION_JSON).headers(limit("14001")));

        salesforce.queryAll(SalesforceClient.Purpose.POLL, "SELECT Id FROM Contact");
        salesforce.decisions(List.of("R-1"));

        var u = usage.usage();
        assertThat(u.byPurpose()).containsEntry("poll", 1L).containsEntry("decisions", 1L).containsEntry("token", 1L);
        // The token is not a call the org counts: ours are the two queries.
        assertThat(u.ours24h()).isEqualTo(2);
        assertThat(u.orgUsed()).isEqualTo(14001);
        assertThat(u.orgMax()).isEqualTo(15000);
        assertThat(u.others24h()).isEqualTo(13999);
        assertThat(u.seenAt()).isEqualTo(clock.instant());
        assertThat(u.paused()).isFalse();
        assertThat(registry.get("salesforce.org.api.used").gauge().value()).isEqualTo(14001);
        assertThat(registry.get("salesforce.budget.paused").gauge().value()).isZero();
    }

    @Test
    void aRefusalForTheAllowanceIsCountedAsLimitedAndPausesTheCalls() {
        token();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(ORG + "/services/data/v67.0/queryAll")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"message\":\"TotalRequests Limit exceeded.\",\"errorCode\":\"REQUEST_LIMIT_EXCEEDED\"}]"));

        assertThatThrownBy(() -> salesforce.queryAll(SalesforceClient.Purpose.POLL, "SELECT Id FROM Contact"))
                .isInstanceOf(SalesforceClient.LimitExceeded.class);

        var u = usage.usage();
        assertThat(u.limited24h()).isEqualTo(1);
        assertThat(u.paused()).isTrue();
        assertThat(u.pausedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
        assertThat(u.orgUsed()).isNull();
        assertThat(u.others24h()).isNull();
        assertThat(registry.get("salesforce.budget.paused").gauge().value()).isEqualTo(1);
    }

    @Test
    void theLimitsAreAskedOnlyWhenNoAnswerHasSaidThemForAQuarterOfAnHour() {
        token();
        server.expect(method(org.springframework.http.HttpMethod.GET)).andExpect(requestTo(ORG + "/services/data/v67.0/limits"))
                .andRespond(withSuccess("{\"DailyApiRequests\":{\"Max\":15000,\"Remaining\":2500}}", MediaType.APPLICATION_JSON));

        usage.refreshLimits();
        assertThat(usage.usage().orgUsed()).isEqualTo(12500);
        assertThat(usage.usage().byPurpose()).containsEntry("limits", 1L);

        // Seen just now: not asked again (the mock would fail on an unexpected call).
        clock.advance(Duration.ofMinutes(10));
        usage.refreshLimits();
        server.verify();
    }

    @Test
    void whatTheOrgSaidAnHourAgoSaysWhetherTheAllowanceFillsUpOrComesBack() {
        budget.observed(14500, 15000);
        clock.advance(Duration.ofMinutes(30));
        budget.observed(14600, 15000);
        clock.advance(Duration.ofMinutes(35));
        budget.observed(14665, 15000);

        var u = usage.usage();
        assertThat(u.orgLeft()).isEqualTo(335);
        // Nearest to an hour ago (65 min, not 35): 14,500.
        assertThat(u.orgUsed1hAgo()).isEqualTo(14500);

        // Nothing said around an hour ago: no trend rather than a wrong one.
        clock.advance(Duration.ofHours(2));
        assertThat(usage.usage().orgUsed1hAgo()).isNull();
    }

    @Test
    void theShellsSeeTheUsageAsAPlainMap() {
        assertThat(usage.usage().byPurpose()).isEqualTo(Map.of());
        assertThat(usage.usage().api()).isEqualTo("salesforce");
    }
}
