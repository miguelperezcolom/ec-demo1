package io.mateu.ecdemo1.communication.routing;

import io.mateu.ecdemo1.communication.config.CommunicationProperties;
import io.mateu.ecdemo1.communication.store.Channel;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The recipients table is the only rule of who hears of what, so it must never start empty — nobody
 * would see anything. Right after the schema is updated, before any consumer starts:
 *
 * <ul>
 *   <li>A recipient of before — e-mail only, and only for what was configured as urgent — is brought
 *       over as exactly that: channel EMAIL, and the urgent types (or the one type it named).</li>
 *   <li>A table with no recipient of the new kind is seeded with what used to be configuration: the
 *       integration's administrators (role ai-admin, inbox and browsers, every type), the Google Chat
 *       spaces (every type and the forms engine's tasks, both spaces), and — on a table that was
 *       empty — the urgent ones e-mailed to {@code communication.default-email}.</li>
 * </ul>
 *
 * <p>Seeding is only for a table with nothing of the new kind: once there is, what is in it is what
 * people decided. To silence a recipient, deactivate it; a table emptied by hand is seeded again on the
 * next start.
 *
 * <p>E-mail used to be every recipient's, and its column was created NOT NULL; ddl-auto update never
 * relaxes a constraint, so it is dropped here — a recipient that is a role's inbox has no address.
 */
@Slf4j
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class RecipientDefaults {

    /** What was configured as urgent before the recipients decided it: these were e-mailed. */
    static final List<String> URGENT = List.of("PMS_REJECTED", "RETRYING_TOO_LONG");
    static final String ADMIN_ROLE = "ai-admin";

    final RecipientRepository recipients;
    final CommunicationProperties properties;
    final JdbcTemplate jdbc;

    @PostConstruct
    public void apply() {
        jdbc.execute("alter table if exists recipient alter column email drop not null");
        var changed = bringOver(recipients.findAll(), properties.defaultEmail());
        recipients.saveAll(changed);
        changed.forEach(r -> log.info("Recipient '{}': {} {} {}", r.name, r.channels, r.types == null ? "all types" : r.types,
                r.roles == null ? "" : "roles " + r.roles));
    }

    /** The recipients to save: those of before, brought over, and the defaults when there is nothing of the new kind. */
    static List<Recipient> bringOver(List<Recipient> existing, String defaultEmail) {
        var changed = new ArrayList<Recipient>();
        var ofBefore = existing.stream().filter(r -> r.channels == null || r.channels.isBlank()).toList();
        for (var r : ofBefore) {
            r.channels = Channel.EMAIL.name();
            r.types = r.legacyType == null || r.legacyType.isBlank() ? String.join(",", URGENT) : r.legacyType;
            r.legacyType = null;
            changed.add(r);
        }
        if (ofBefore.size() == existing.size()) {
            changed.add(recipient("Integration administrators", null, EnumSet.of(Channel.INBOX, Channel.WEB_PUSH), false,
                    ADMIN_ROLE, null));
            changed.add(recipient("Google Chat", null, EnumSet.of(Channel.GOOGLE_CHAT), true, null, null));
            if (existing.isEmpty() && defaultEmail != null && !defaultEmail.isBlank()) {
                changed.add(recipient("Urgent, by e-mail", defaultEmail, EnumSet.of(Channel.EMAIL), false, null,
                        String.join(",", URGENT)));
            }
        }
        return changed;
    }

    static Recipient recipient(String name, String email, EnumSet<Channel> channels, boolean tasks, String roles, String types) {
        var r = new Recipient();
        r.id = "default-" + name.toLowerCase().replaceAll("[^a-z]+", "-").replaceAll("-$", "");
        r.name = name;
        r.email = email;
        r.channels = Recipient.join(channels);
        r.tasks = tasks;
        r.roles = roles;
        r.types = types;
        r.active = true;
        return r;
    }
}
