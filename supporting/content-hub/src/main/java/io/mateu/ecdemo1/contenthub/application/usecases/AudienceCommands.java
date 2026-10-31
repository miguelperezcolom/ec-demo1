package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AudienceCommands {

    final NamedAudienceRepository audiences;
    final NamedAudiences namedAudiences;
    final EmployeeDirectory directory;
    final DomainEvents events;

    @Transactional
    public void define(Reader reader, String id, String name, Audience expression) {
        reader.require(EditorialPermission.ADMIN);
        if (audiences.find(id).isPresent()) {
            throw new IllegalArgumentException("Ya hay una audiencia " + id);
        }
        audiences.save(NamedAudience.define(id, name, expression));
    }

    @Transactional
    public void redefine(Reader reader, String id, String name, Audience expression) {
        reader.require(EditorialPermission.ADMIN);
        var audience = audiences.find(id).orElseThrow();
        audience.rename(name);
        audience.redefine(expression);
        audiences.save(audience);
        events.drain(audience);
    }

    /** Deleting one leaves whatever named it visible to nobody, until it is edited. */
    @Transactional
    public void delete(Reader reader, String id) {
        reader.require(EditorialPermission.ADMIN);
        audiences.delete(id);
        events.publish(new ContentEvents.AudienceRedefined(id));
    }

    /** How many employees of the directory meet an audience — the editor's count, 0 meaning a warning. */
    public long count(Audience audience) {
        var policy = namedAudiences.policy();
        return directory.all().stream().filter(Employee::enabled)
                .filter(e -> policy.admits(audience, e.attributes())).count();
    }

    /**
     * The profiles of the directory an audience admits, with how many employees each — what "ver como"
     * offers for a named audience. Largest first.
     */
    public List<Profile> profilesIn(Audience audience) {
        var policy = namedAudiences.policy();
        var byHash = new LinkedHashMap<String, Profile>();
        for (var e : directory.all()) {
            if (e.enabled() && policy.admits(audience, e.attributes())) {
                byHash.merge(e.hash(), new Profile(e.hash(), e.attributes(), 1),
                        (a, b) -> new Profile(a.hash(), a.attributes(), a.employees() + 1));
            }
        }
        return byHash.values().stream().sorted(Comparator.comparingLong(Profile::employees).reversed()).toList();
    }

    public Map<String, Profile> allProfiles() {
        var out = new LinkedHashMap<String, Profile>();
        profilesIn(Audience.EVERYONE).forEach(p -> out.put(p.hash(), p));
        return out;
    }

    public record Profile(String hash, EmployeeAttributes attributes, long employees) {
    }
}
