package io.mateu.ecdemo1.contenthub.infra.config;

import io.mateu.ecdemo1.contenthub.application.health.ContentHealth;
import io.mateu.ecdemo1.contenthub.application.health.HealthSettings;
import io.mateu.ecdemo1.contenthub.application.health.LinkChecker;
import io.mateu.ecdemo1.contenthub.application.out.AltTextGenerator;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.HealthRepository;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaDescriptions;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.infra.out.health.JdkHttpProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** «Salud del contenido»: its settings ({@code content-hub.health.*}), the link checker and the service. */
@Configuration
public class HealthConfig {

    @Bean
    HealthSettings healthSettings(@Value("${content-hub.health.expiry-days:14}") int expiryDays,
                                  @Value("${content-hub.health.stale-months:12}") int staleMonths,
                                  @Value("${content-hub.health.stale-sections:}") String staleSections,
                                  @Value("${content-hub.health.unviewed-days:90}") int unviewedDays,
                                  @Value("${content-hub.health.external-links:true}") boolean externalLinks,
                                  @Value("${content-hub.health.link-timeout:PT5S}") Duration linkTimeout,
                                  @Value("${content-hub.health.per-host-delay:PT1S}") Duration perHostDelay,
                                  @Value("${content-hub.health.max-external-per-run:300}") int maxExternal,
                                  @Value("${content-hub.health.ok-for:P7D}") Duration okFor,
                                  @Value("${content-hub.health.broken-for:P1D}") Duration brokenFor,
                                  @Value("${content-hub.health.user-agent:}") String userAgent,
                                  @Value("${content-hub.health.orphan-grace:P1D}") Duration orphanGrace) {
        return new HealthSettings(expiryDays, staleMonths, csv(staleSections), unviewedDays, externalLinks, linkTimeout,
                perHostDelay, maxExternal, okFor, brokenFor, userAgent, orphanGrace);
    }

    @Bean
    LinkChecker linkChecker(HealthRepository store, HealthSettings settings, Clock clock) {
        return new LinkChecker(new JdkHttpProbe(), store, settings, clock, d -> Thread.sleep(d.toMillis()));
    }

    @Bean
    ContentHealth contentHealth(ItemRepository items, MediaRepository media, MediaDescriptions descriptions,
                                SlotRepository slots, HomePages homes, NavigationRepository navigation,
                                MenuRepository menus, UiSources uiSources, EmployeeDirectory directory,
                                SurveyRepository surveys, NewsletterRepository newsletters,
                                io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository issues,
                                io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites, UsageRepository usage,
                                io.mateu.ecdemo1.contenthub.application.out.TemplateRepository templates,
                                HealthRepository store, LinkChecker links, AltTextGenerator ai, HealthSettings settings,
                                Clock clock,
                                @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl,
                                @Value("${content-hub.health.internal-hosts:}") String internalHosts) {
        var hosts = new LinkedHashSet<String>();
        csv(internalHosts).forEach(h -> hosts.add(h.toLowerCase(Locale.ROOT)));
        try {
            var host = URI.create(portalUrl).getHost();
            if (host != null) {
                hosts.add(host.toLowerCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException e) {
            // no portal URL: only the configured hosts are ours
        }
        return new ContentHealth(new ContentHealth.Sources(items, media, descriptions, slots, homes, navigation, menus,
                uiSources, directory, surveys, newsletters, issues, sites, usage, templates), store, links, ai, settings, hosts, clock,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    static Set<String> csv(String value) {
        return value == null || value.isBlank() ? Set.of() : Arrays.stream(value.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
