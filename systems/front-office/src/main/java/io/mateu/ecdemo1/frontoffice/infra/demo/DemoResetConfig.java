package io.mateu.ecdemo1.frontoffice.infra.demo;

import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the front office empties when the demo goes back to zero (process reset-demo), as
 * deploy/demo/zero.sh did: the guests, the stays and all that hangs from them, the PMS's catalogue
 * as it was read, the notices kept for the desk; and every room free again. The rooms, the charges
 * and add-ons on sale, the automations and the registration rules stay — they are set up.
 */
@Configuration
public class DemoResetConfig {

  @Bean
  public DemoResetPlan demoResetPlan() {
    return DemoResetPlan.truncate("front-office",
            "guest", "guest_kardex", "guest_preference", "stay", "stay_add_on", "stay_companion", "stay_incident",
            "folio", "folio_line", "pms_catalogue", "pms_catalogue_sync", "command_inbox", "walk_in", "check_in_ops",
            "forced_check_in", "stay_invoice", "folio_line_pms", "pax_registration_data", "customer_nationality",
            "customer_notice", "stay_notice_ack")
        .then("update room set occupancy = 'FREE'");
  }
}
