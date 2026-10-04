package io.mateu.ecdemo1.booking.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.NoShowPolicy;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class BookingConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    CrsCatalog crsCatalog(ObjectMapper objectMapper) {
        return built(objectMapper);
    }

    /** The catalog as built, before any rate plan opened while the CRS runs. */
    static CrsCatalog built(ObjectMapper objectMapper) {
        return CrsCatalog.standard(new ImportedCatalogs(objectMapper)::hotel);
    }

    /** The CRS's no-show rule: the share of the original price a guest who does not arrive owes. */
    @Bean
    NoShowPolicy noShowPolicy(@Value("${booking.no-show-fee-percent:25}") int feePercent) {
        return new NoShowPolicy(feePercent);
    }

    @Bean
    RoomPricing roomPricing() {
        return new RoomPricing();
    }

    /**
     * The JSON columns are written with Spring's ObjectMapper rather than the one Hibernate would
     * build for itself, so dates are stored as ISO strings — readable in the table — and not as
     * arrays of numbers.
     */
    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
