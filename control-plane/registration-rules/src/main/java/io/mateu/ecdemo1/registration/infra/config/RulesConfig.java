package io.mateu.ecdemo1.registration.infra.config;

import io.mateu.ecdemo1.registration.application.RegistrationRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;

@Configuration
@EnableScheduling
public class RulesConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** {@code registration.hotel-countries}: {@code MRU01=MU,PMI01=ES}. */
    @Bean
    RegistrationRules.HotelCountries hotelCountries(@Value("${registration.hotel-countries:}") String value) {
        var map = new LinkedHashMap<String, String>();
        for (var pair : value.split(",")) {
            var kv = pair.split("=");
            if (kv.length == 2 && !kv[0].isBlank() && !kv[1].isBlank()) {
                map.put(kv[0].trim().toUpperCase(Locale.ROOT), kv[1].trim().toUpperCase(Locale.ROOT));
            }
        }
        return new RegistrationRules.HotelCountries(map);
    }
}
