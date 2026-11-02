package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The target kinds a newsletter's menu adds to the content-hub's (its pages and sections are the standard
 * PAGE and SECTION, under the newsletter's routes — SiteRoutes): its home, its archive, and one issue. They
 * only apply to a newsletter's site; an issue is shown once it has been sent.
 */
public final class NewsletterTargets {

    public static final String HOME = "NEWSLETTER_HOME";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String ISSUE = "ISSUE";

    private NewsletterTargets() {
    }

    static boolean newsletter(SiteRepository sites, String siteId) {
        return sites.find(siteId).map(s -> s.kind() == Site.Kind.NEWSLETTER).orElse(false);
    }

    /** The newsletter's home: its latest issue and the ones before. Value: the site's id. */
    @Component
    @RequiredArgsConstructor
    public static class Home implements MenuTargetResolver {

        final SiteRepository sites;
        final SiteRoutes routes;

        @Override
        public String kind() {
            return HOME;
        }

        @Override
        public String label() {
            return "Inicio de la newsletter";
        }

        @Override
        public boolean appliesTo(String siteId) {
            return newsletter(sites, siteId);
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return Optional.of(new MenuLink.Route(routes.base(context.siteId())));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return List.of(new Choice(siteId, "Inicio"));
        }
    }

    /** All its sent issues. Value: the site's id. */
    @Component
    @RequiredArgsConstructor
    public static class Archive implements MenuTargetResolver {

        final SiteRepository sites;
        final SiteRoutes routes;

        @Override
        public String kind() {
            return ARCHIVE;
        }

        @Override
        public String label() {
            return "Números anteriores";
        }

        @Override
        public boolean appliesTo(String siteId) {
            return newsletter(sites, siteId);
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return Optional.of(new MenuLink.Route(routes.base(context.siteId()) + "/archivo"));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return List.of(new Choice(siteId, "Números anteriores"));
        }
    }

    /** One issue, by id: shown once it has been sent (its permanent page). */
    @Component
    @RequiredArgsConstructor
    public static class Issue implements MenuTargetResolver {

        final SiteRepository sites;
        final NewsletterIssueRepository issues;
        final SiteRoutes routes;

        @Override
        public String kind() {
            return ISSUE;
        }

        @Override
        public String label() {
            return "Número";
        }

        @Override
        public boolean appliesTo(String siteId) {
            return newsletter(sites, siteId);
        }

        @Override
        public Optional<MenuLink> resolve(MenuContext context, MenuTarget target) {
            return issues.find(target.value())
                    .filter(i -> i.newsletterId().equals(context.siteId()) && i.status() == NewsletterIssue.Status.SENT)
                    .map(i -> new MenuLink.Route(routes.base(context.siteId()) + "/numeros/" + i.id()));
        }

        @Override
        public List<Choice> choices(String siteId) {
            return issues.ofNewsletter(siteId).stream().filter(i -> i.status() == NewsletterIssue.Status.SENT)
                    .map(i -> new Choice(i.id(), "nº " + i.number() + " · " + i.subject())).toList();
        }
    }
}
