package io.mateu.ecdemo1.partners.infra.config;

import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.application.usecases.PartnerService;
import io.mateu.ecdemo1.partners.domain.partner.Address;
import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;
import io.mateu.ecdemo1.partners.infra.out.outbox.OutboxProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class PartnersConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * A handful of partners so the demo has something to sell through. Fictional, like every piece
     * of data here: it travels to a PMS.
     */
    @Bean
    ApplicationRunner seedPartners(PartnerRepository repository, PartnerService service) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            service.create("NORDTRAVEL", new PartnerDetails(PartnerType.TourOperator, "Nordic Travel Group AB",
                    "SE556677889901", new Address("Kungsgatan 10", "Stockholm", "11143", "SE"),
                    "billing@nordtravel.example", "+46 8 000 00 00", BillingMode.NoFront));
            service.create("BOOKIT", new PartnerDetails(PartnerType.OnlineAgency, "Bookit Online S.L.",
                    "B12345678", new Address("Calle Mayor 1", "Madrid", "28013", "ES"),
                    "partners@bookit.example", "+34 910 000 000", BillingMode.Front));
            service.create("VIAJESSOL", new PartnerDetails(PartnerType.TravelAgent, "Viajes Sol S.A.",
                    "A87654321", new Address("Avinguda Jaume III 5", "Palma", "07012", "ES"),
                    "reservas@viajessol.example", "+34 971 000 000", BillingMode.NoFront));
            service.create("ACME", new PartnerDetails(PartnerType.Company, "Acme Corporation Ltd",
                    "GB123456789", new Address("1 Main Street", "London", "EC1A 1AA", "GB"),
                    "travel@acme.example", "+44 20 0000 0000", BillingMode.NoFront));
        };
    }
}
