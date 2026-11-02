package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.site.Site;

import java.util.List;
import java.util.Optional;

/** The sites content-hub hosts: the intranet, and one per newsletter. */
public interface SiteRepository {

    Optional<Site> find(String id);

    void save(Site site);

    List<Site> findAll();

    /** The site, or the intranet as it is by default when it is the intranet and nobody stored it. */
    default Optional<Site> findOrDefault(String id) {
        var site = find(Site.of(id));
        return site.isEmpty() && Site.INTRANET.equals(Site.of(id)) ? Optional.of(Site.intranet()) : site;
    }
}
