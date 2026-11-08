package io.mateu.ecdemo1.frontoffice.infra.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The mobile app reads the manifest before anyone signs in; the UI's own calls still need a token. */
@SpringBootTest(properties =
    "spring.datasource.url=jdbc:h2:mem:mobile-manifest;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@AutoConfigureMockMvc
class MobileManifestSecurityTest {

  @Autowired MockMvc mvc;

  @Test
  void theManifestIsPublic() throws Exception {
    mvc.perform(get("/mateu/v3/manifest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Front-Office Suite"));
  }

  @Test
  void theRestOfTheUiStillNeedsAToken() throws Exception {
    mvc.perform(post("/mateu/v3/sync").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
