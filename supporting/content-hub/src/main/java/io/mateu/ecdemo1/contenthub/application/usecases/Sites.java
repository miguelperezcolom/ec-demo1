package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The sites content-hub hosts. The intranet is always there (stored the first time the service starts, as it
 * is by default); a newsletter's site is created with the newsletter (NewsletterEditorial).
 */
@Service
@RequiredArgsConstructor
public class Sites {

    final SiteRepository sites;

    /** Stores the intranet as a site if it is not yet: idempotent, every start. */
    @Transactional
    public boolean ensureIntranet() {
        if (sites.find(Site.INTRANET).isPresent()) {
            return false;
        }
        sites.save(Site.intranet());
        return true;
    }

    public Optional<Site> find(String id) {
        return sites.findOrDefault(id);
    }

    public List<Site> all() {
        return sites.findAll();
    }

    public List<Site> ofKind(Site.Kind kind) {
        return sites.findAll().stream().filter(s -> s.kind() == kind).toList();
    }

    /** The intranet's identity is configuration (content:admin); a newsletter's, its editors' (content:edit). */
    @Transactional
    public void configure(Reader reader, String id, Site.Definition definition) {
        reader.require(Site.INTRANET.equals(Site.of(id)) ? EditorialPermission.ADMIN : EditorialPermission.EDIT);
        var site = sites.findOrDefault(id).orElseThrow(() -> new IllegalArgumentException("No existe el sitio " + id));
        site.configure(definition);
        sites.save(site);
    }
}
