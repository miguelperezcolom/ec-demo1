package io.mateu.ecdemo1.booking.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import io.mateu.ecdemo1.booking.infra.out.outbox.OutboxProperties;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class BookingConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    CrsCatalog crsCatalog() {
        return CrsCatalog.standard();
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
