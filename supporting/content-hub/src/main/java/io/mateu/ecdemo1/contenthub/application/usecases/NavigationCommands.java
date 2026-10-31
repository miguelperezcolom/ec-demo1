package io.mateu.ecdemo1.contenthub.application.usecases;

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

    @Transactional
    public void add(Reader reader, String id, String parentId, Map<String, String> names) {
        reader.require(EditorialPermission.ADMIN);
        var nav = navigation.get();
        nav.add(id, blankToNull(parentId), names);
        navigation.save(nav);
        events.drain(nav);
    }

    @Transactional
    public void rename(Reader reader, String id, Map<String, String> names) {
        reader.require(EditorialPermission.ADMIN);
        var nav = navigation.get();
        nav.rename(id, names);
        navigation.save(nav);
        events.drain(nav);
    }

    @Transactional
    public void move(Reader reader, String id, String parentId, int position) {
        reader.require(EditorialPermission.ADMIN);
        var nav = navigation.get();
        nav.move(id, blankToNull(parentId), position);
        navigation.save(nav);
        events.drain(nav);
    }

    @Transactional
    public void remove(Reader reader, String id) {
        reader.require(EditorialPermission.ADMIN);
        var nav = navigation.get();
        nav.remove(id);
        navigation.save(nav);
        events.drain(nav);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
