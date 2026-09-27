package io.mateu.ecdemo1.mdm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MdmProperties.class, io.mateu.ecdemo1.mdm.footprint.LinksProperties.class})
public class MdmConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** Salesforce's daily API allowance: how long calls pause when it is spent, at first and at most. */
    @Bean
    io.mateu.ecdemo1.mdm.salesforce.SalesforceBudget salesforceBudget(Clock clock,
            @org.springframework.beans.factory.annotation.Value("${mdm.salesforce-pause:5m}") java.time.Duration first,
            @org.springframework.beans.factory.annotation.Value("${mdm.salesforce-pause-max:60m}") java.time.Duration longest) {
        return new io.mateu.ecdemo1.mdm.salesforce.SalesforceBudget(clock, first, longest);
    }
}
