package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.domain.site.Site;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NavigationCommands {

    final NavigationRepository navigation;
    final DomainEvents events;

    /** In the intranet. */
    @Transactional
    public void add(Reader reader, String id, String parentId, Map<String, String> names) {
        add(reader, Site.INTRANET, id, parentId, names);
    }

    @Transactional
    public void add(Reader reader, String siteId, String id, String parentId, Map<String, String> names) {
        require(reader, siteId);
        var nav = navigation.get(siteId);
        nav.add(id, blankToNull(parentId), names);
        navigation.save(siteId, nav);
        events.drain(nav);
    }

    /** In the intranet. */
    @Transactional
    public void rename(Reader reader, String id, Map<String, String> names) {
        rename(reader, Site.INTRANET, id, names);
    }

    @Transactional
    public void rename(Reader reader, String siteId, String id, Map<String, String> names) {
        require(reader, siteId);
        var nav = navigation.get(siteId);
        nav.rename(id, names);
        navigation.save(siteId, nav);
        events.drain(nav);
    }

    /** In the intranet. */
    @Transactional
    public void move(Reader reader, String id, String parentId, int position) {
        move(reader, Site.INTRANET, id, parentId, position);
    }

    @Transactional
    public void move(Reader reader, String siteId, String id, String parentId, int position) {
        require(reader, siteId);
        var nav = navigation.get(siteId);
        nav.move(id, blankToNull(parentId), position);
        navigation.save(siteId, nav);
        events.drain(nav);
    }

    /** In the intranet. */
    @Transactional
    public void remove(Reader reader, String id) {
        remove(reader, Site.INTRANET, id);
    }

    @Transactional
    public void remove(Reader reader, String siteId, String id) {
        require(reader, siteId);
        var nav = navigation.get(siteId);
        nav.remove(id);
        navigation.save(siteId, nav);
        events.drain(nav);
    }

    /** The intranet's tree is configuration (content:admin); a newsletter's is its editors' (content:edit). */
    static void require(Reader reader, String siteId) {
        reader.require(Site.INTRANET.equals(Site.of(siteId)) ? EditorialPermission.ADMIN : EditorialPermission.EDIT);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
