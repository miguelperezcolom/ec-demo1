package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The context the CRS locator goes under in Opera: ECDEMO1 when nothing says otherwise, and whatever
 * OPERA_EXTERNAL_SYSTEM says — in ec1, the ec-demo-run ConfigMap that zero.sh renews on every run.
 */
class OhipPropertiesTest {

    static OhipProperties bind(Map<String, Object> environment) throws Exception {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("test-env", environment));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            env.getPropertySources().addLast(source);
        }
        return Binder.get(env).bind("ohip", OhipProperties.class).get();
    }

    @Test
    void theContextIsEcdemo1WhenNothingSaysOtherwise() throws Exception {
        assertThat(bind(Map.of()).externalSystemCode()).isEqualTo("ECDEMO1");
    }

    @Test
    void theRunsContextComesFromTheEnvironment() throws Exception {
        assertThat(bind(Map.of("OPERA_EXTERNAL_SYSTEM", "ECDEMO1-09271915")).externalSystemCode())
                .isEqualTo("ECDEMO1-09271915");
    }

    @Test
    void withNoConfigurationAtAllTheRecordDefaultsToEcdemo1() {
        assertThat(new OhipProperties(null, null, null, null, null, null, null, null, null).externalSystemCode())
                .isEqualTo("ECDEMO1");
    }
}
