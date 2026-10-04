package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.infra.in.ui.BookingHome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Call center → Catalogue → Rate plans: a hotel's rate plans listed, and a new one opened from the
 * screen through the same use case as the REST API's — sellable at once and kept for a restart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RatePlansScreenTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw,size=8g"));

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    CrsCatalog catalog;
    @Autowired
    JdbcTemplate jdbc;

    String wire(String route, String consumedRoute) throws Exception {
        return wire(route, consumedRoute, BookingHome.class.getName());
    }

    String wire(String route, String consumedRoute, String serverSideType) throws Exception {
        var body = json.writeValueAsString(Map.of("route", route, "consumedRoute", consumedRoute, "actionId", "",
                "componentState", Map.of(), "appState", Map.of(), "serverSideType", serverSideType));
        var started = mvc.perform(post("/_booking/mateu/v3/sync" + route).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void theListingRendersOverTheWire() throws Exception {
        assertThat(wire("/booking/catalogue/ratePlans", "")).contains(RatePlansCrud.class.getName())
                .doesNotContain("Not found.").doesNotContain("\"variant\":\"error\"");
        var listing = wire("/booking/catalogue/ratePlans", "/booking/catalogue/ratePlans", RatePlansCrud.class.getName());
        assertThat(listing).contains("Rate plans").doesNotContain("Not found.").doesNotContain("\"variant\":\"error\"");
    }

    @Test
    void aHotelsRatePlansAreListedAndNewOpensOne() {
        var crud = context.getBean(RatePlansCrud.class);
        assertThat(crud.rows("PMI01", null)).extracting(RatePlanRow::code).containsExactly("BAR", "EB", "NRF", "TTOO");
        assertThat(crud.rows("MRU01", "EMPLEADOS-28")).isEmpty();

        var form = context.getBean(RatePlanForm.class);
        form.hotelCode = "MRU01";
        form.code = "EMPLEADOS-28";
        form.name = "Empleados 2028";
        form.factor = new BigDecimal("0.5");
        assertThat(form.create(null)).isEqualTo("MRU01/EMPLEADOS-28");

        assertThat(crud.rows("MRU01", "empleados-28")).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Empleados 2028");
            assertThat(r.factor()).isEqualTo("0.5");
        });
        assertThat(catalog.ratePlan("MRU01", "EMPLEADOS-28").factor()).isEqualByComparingTo("0.5");
        assertThat(jdbc.queryForObject("select added_by from catalog_rate_plan where code = 'EMPLEADOS-28'",
                String.class)).isEqualTo("call center");

        var bad = context.getBean(RatePlanForm.class);
        bad.hotelCode = "MRU01";
        bad.code = "empleados 28";
        bad.name = "x";
        bad.factor = BigDecimal.ONE;
        assertThatThrownBy(() -> bad.create(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
