package io.mateu.ecdemo1.frontoffice.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The hotels the front office serves, as the pms-fo integration reads them when it is registered. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:hotels-api;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "frontoffice.hotel=MRU01", "frontoffice.pms-hotel=XMAR", "frontoffice.hotel-name=Riu Demo Mauricio"})
@AutoConfigureMockMvc
class HotelsApiTest {

  @Autowired MockMvc mvc;

  @Test
  void theConfiguredHotelWithItsPmsProperty() throws Exception {
    mvc.perform(get("/api/hotels"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("MRU01"))
        .andExpect(jsonPath("$[0].name").value("Riu Demo Mauricio"))
        .andExpect(jsonPath("$[0].pmsHotelCode").value("XMAR"));
  }
}
