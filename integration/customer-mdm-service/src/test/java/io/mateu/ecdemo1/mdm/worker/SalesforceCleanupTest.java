package io.mateu.ecdemo1.mdm.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.mdm.config.MdmConfig;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.salesforce.ConsolidationEvents;
import io.mateu.ecdemo1.mdm.salesforce.ConsolidationPoll;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceBudget;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClients;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mateu.workflow.worker.api.TaskFailure;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * clean-salesforce against a Salesforce played by MockRestServiceServer: the change Cases, then every
 * contact, two hundred a call; the cursors moved as zero.sh moves them; the subscription restarted.
 */
class SalesforceCleanupTest {

    static final String ORG = "https://acme.my.salesforce.com";
    static final String DATA = ORG + "/services/data/v67.0";
    static final String TOKEN = """
            {"access_token":"t","instance_url":"https://acme.my.salesforce.com","id":"https://login/id/00D000000000001/005000000000001"}""";

    final MdmProperties properties = new MdmProperties(
            new MdmProperties.Salesforce("acme.my.salesforce.com", "id", "secret", "v67.0", null, 0, false),
            Duration.ofSeconds(5), Duration.ofMinutes(15));
    final SalesforceBudget budget = new SalesforceBudget(Clock.systemUTC(), Duration.ofMinutes(5), Duration.ofMinutes(60));
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final SalesforceClient salesforce = SalesforceClients.of(properties, new TolerantReader(new ObjectMapper()), budget,
            new MdmConfig().salesforceCalls(Clock.systemUTC(), new SimpleMeterRegistry()), builder);
    final ConsolidationEvents events = Mockito.mock(ConsolidationEvents.class);
    final ConsolidationPoll poll = Mockito.mock(ConsolidationPoll.class);
    final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    final SalesforceCleanup cleanup = new SalesforceCleanup(salesforce, events, poll, jdbc);

    static List<String> ids(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> prefix + String.format("%015d", i)).toList();
    }

    static String page(List<String> ids, String next) {
        var records = String.join(",", ids.stream().map(id -> "{\"Id\":\"" + id + "\"}").toList());
        return next == null
                ? "{\"done\":true,\"records\":[" + records + "]}"
                : "{\"done\":false,\"nextRecordsUrl\":\"" + next + "\",\"records\":[" + records + "]}";
    }

    static String deleted(int n) {
        return "[" + String.join(",", IntStream.range(0, n).mapToObj(i -> "{\"success\":true,\"errors\":[]}").toList()) + "]";
    }

    void expectDelete(int n) {
        server.expect(once(), requestTo(Matchers.startsWith(DATA + "/composite/sobjects?allOrNone=false&ids=")))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(r -> assertThat(r.getURI().getRawQuery().split("ids=")[1].split("%2C|,")).hasSize(n))
                .andRespond(withSuccess(deleted(n), MediaType.APPLICATION_JSON));
    }

    @Test
    void theCasesThenEveryContactTwoHundredACallThenTheCursorsFromNow() {
        Mockito.when(events.isRunning()).thenReturn(true);
        var contacts = ids("003", 450);
        server.expect(requestTo(ORG + "/services/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.allOf(Matchers.startsWith(DATA + "/query?q="), Matchers.containsString("Case"))))
                .andRespond(withSuccess(page(ids("500", 3), null), MediaType.APPLICATION_JSON));
        expectDelete(3);
        server.expect(requestTo(Matchers.allOf(Matchers.startsWith(DATA + "/query?q="), Matchers.containsString("Contact"))))
                .andRespond(withSuccess(page(contacts.subList(0, 300), "/services/data/v67.0/query/01g-300"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(DATA + "/query/01g-300"))
                .andRespond(withSuccess(page(contacts.subList(300, 450), null), MediaType.APPLICATION_JSON));
        expectDelete(200);
        expectDelete(200);
        expectDelete(50);

        var summary = cleanup.clean();

        server.verify();
        assertThat(summary).isEqualTo("3 Case(s), 450 contacto(s)");
        var order = Mockito.inOrder(events, jdbc);
        order.verify(events).stop();
        order.verify(jdbc).update("delete from salesforce_cursor where name like 'pubsub%'");
        order.verify(jdbc).update("update salesforce_cursor set until = now() where name = 'poll'");
        order.verify(events).start();
    }

    @Test
    void idempotent_whatIsGoneIsNotFoundAgain() {
        server.expect(requestTo(ORG + "/services/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(DATA + "/query?q="))).andRespond(withSuccess(page(List.of(), null), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(DATA + "/query?q="))).andRespond(withSuccess(page(List.of(), null), MediaType.APPLICATION_JSON));

        assertThat(cleanup.clean()).isEqualTo("0 Case(s), 0 contacto(s)");
        server.verify(); // no DELETE at all
        Mockito.verify(events, Mockito.never()).start(); // it was not subscribed: not started either
    }

    @Test
    void theDailyAllowanceSpentIsSalesforceLimit_andTheSubscriptionComesBack() {
        Mockito.when(events.isRunning()).thenReturn(true);
        server.expect(requestTo(ORG + "/services/oauth2/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(DATA + "/query?q="))).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("[{\"message\":\"TotalRequests Limit exceeded.\",\"errorCode\":\"REQUEST_LIMIT_EXCEEDED\"}]"));
        var task = new MdmTasks().cleanSalesforceTask(cleanup);

        assertThatThrownBy(() -> task.handler().handle(new MdmTasks.CleanSalesforce("k"), MdmResetTest.context()))
                .isInstanceOfSatisfying(TaskFailure.class, f -> assertThat(f.code()).isEqualTo("SALESFORCE_LIMIT"));
        Mockito.verify(events).start();
        Mockito.verifyNoInteractions(jdbc);
    }
}
