package io.mateu.ecdemo1.contenthub.application.directory;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Applies the users service's messages to the directory. A new combination of attributes is
 * materialised in the projection at once, so the employee sees the right content on their next request;
 * there is no other per-user state to keep in step.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectoryUpdates {

    final EmployeeDirectory directory;
    final Projection projection;
    final DomainEvents events;

    @Transactional
    public void apply(EmployeeMessage message) {
        if (message.userId() == null || message.userId().isBlank()) {
            log.warn("Employee message without a user, ignored: {}", message);
            return;
        }
        if (EmployeeMessage.REMOVED.equals(message.type())) {
            directory.remove(message.userId()).ifPresent(old ->
                    events.publish(new ContentEvents.EmployeeChanged(old.userId(), old.hash(), null)));
            return;
        }
        var attributes = attributes(message.attributes());
        var employee = new Employee(message.userId(), message.name(), message.email(),
                message.enabled() == null || message.enabled(), attributes);
        var old = directory.upsert(employee);
        var oldHash = old.map(Employee::hash).orElse(null);
        if (employee.enabled()) {
            projection.materialise(attributes);
        }
        if (old.isEmpty() || !employee.hash().equals(oldHash)) {
            events.publish(new ContentEvents.EmployeeChanged(employee.userId(), oldHash, employee.hash()));
        }
    }

    /** The message's attributes: the visibility ones by their key, the language apart. Unknown keys are dropped. */
    public static EmployeeAttributes attributes(Map<String, String> raw) {
        var values = new EnumMap<Attribute, String>(Attribute.class);
        String language = null;
        if (raw != null) {
            for (var e : raw.entrySet()) {
                if ("language".equalsIgnoreCase(e.getKey())) {
                    language = e.getValue();
                } else {
                    Attribute.ofKey(e.getKey()).ifPresent(a -> values.put(a, e.getValue()));
                }
            }
        }
        return new EmployeeAttributes(values, language);
    }
}
