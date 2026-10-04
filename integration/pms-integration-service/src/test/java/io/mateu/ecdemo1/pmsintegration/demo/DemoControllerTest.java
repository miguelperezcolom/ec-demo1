package io.mateu.ecdemo1.pmsintegration.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import io.mateu.ecdemo1.pmsintegration.config.OperaContext;
import io.mateu.ecdemo1.pmsintegration.config.RetryAlert;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaOutage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The demo page's controls of the connector, as the control plane calls them. */
class DemoControllerTest {

    final OperaOutageTest.MovingClock clock = new OperaOutageTest.MovingClock();
    final OperaOutage outage = new OperaOutage(clock, Duration.ZERO);
    final RetryAlert alert = new RetryAlert(Duration.ofMinutes(10));
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        // As Boot's: java.time as ISO strings, not numbers.
        var mapper = Jackson2ObjectMapperBuilder.json().build();
        mvc = MockMvcBuilders.standaloneSetup(new DemoController(outage, alert, new OperaContext("ECDEMO1-10031742", "ECDEMO1-10031742")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void statusSaysOffTheThresholdAndTheContext() throws Exception {
        mvc.perform(get("/demo/opera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outage.active").value(false))
                .andExpect(jsonPath("$.outage.until").doesNotExist())
                .andExpect(jsonPath("$.alertAfter").value("PT10M"))
                .andExpect(jsonPath("$.defaultAlertAfter").value("PT10M"))
                .andExpect(jsonPath("$.operaContext").value("ECDEMO1-10031742"))
                .andExpect(jsonPath("$.operaCustomReference").value("ECDEMO1-10031742"));
    }

    @Test
    void onWithAnAlertThresholdThenOffRestoringIt() throws Exception {
        mvc.perform(post("/demo/opera/outage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"autoOffMinutes\":15,\"alertAfterMinutes\":2,\"by\":\"ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outage.active").value(true))
                .andExpect(jsonPath("$.outage.since").value("2026-10-03T17:00:00Z"))
                .andExpect(jsonPath("$.outage.until").value("2026-10-03T17:15:00Z"))
                .andExpect(jsonPath("$.outage.by").value("ana"))
                .andExpect(jsonPath("$.alertAfter").value("PT2M"));

        mvc.perform(post("/demo/opera/outage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"restoreAlert\":true,\"by\":\"ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outage.active").value(false))
                .andExpect(jsonPath("$.alertAfter").value("PT10M"));
    }

    @Test
    void theThresholdAlone() throws Exception {
        mvc.perform(put("/demo/opera/alert-after").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":3,\"by\":\"ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertAfter").value("PT3M"));
    }

    @Test
    void outOfRangeIsABadRequest() throws Exception {
        mvc.perform(post("/demo/opera/outage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"autoOffMinutes\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/demo/opera/outage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"alertAfterMinutes\":121}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/demo/opera/outage").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/demo/opera/alert-after").contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
