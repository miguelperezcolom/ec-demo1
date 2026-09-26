package io.mateu.ecdemo1.communication.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * One subscription, and the only rule of who is told what and where: <em>who</em> (Keycloak users
 * and/or realm roles, or just an e-mail address), <em>what</em> (notification types — none named is
 * all of them — the forms engine's tasks or not, and a hotel — empty is every hotel) and <em>where</em>
 * (its channels). A notification reaches every active recipient that wants it, on each of its
 * channels. Per hotel is how the isolation the rest of the design keeps reaches the people too (HLA R31).
 *
 * <p>The lists are kept comma-separated, the way the rest of this service keeps them: a handful of
 * values each, read whole and never queried by.
 */
@Entity
@Table(name = "recipient")
@NoArgsConstructor
@Getter
@Setter
public class Recipient {

    public static final String TASK = "TASK";

    @Id
    public String id;
    @Column(nullable = false)
    public String name;
    /** Where EMAIL goes; only a recipient with that channel needs one. */
    public String email;
    /** The notification types it wants, comma-separated; none is every type. */
    @Column(length = 500)
    public String types;
    /** Whether it also wants the forms engine's tasks, which are nobody's notification type. */
    @Column(columnDefinition = "boolean not null default false")
    public boolean tasks;
    public String hotelCode;
    public boolean active;
    /** Comma-separated {@link Channel} names. None is e-mail alone: what every recipient was before channels. */
    @Column(length = 100)
    public String channels;
    /** The Google Chat spaces, by number (1, 2…), comma-separated; empty is every configured space. */
    @Column(length = 100)
    public String chatSpaces;
    /** The people it is, by Keycloak username, comma-separated. */
    @Column(length = 1000)
    public String users;
    /** The realm roles it is: everyone who has one of them. Comma-separated. */
    @Column(length = 1000)
    public String roles;
    /**
     * The one type a recipient wanted before it could want several. Read once, when the recipients
     * of before are brought over (RecipientDefaults), and emptied; nothing else looks at it.
     */
    @Column(name = "notification_type", length = 60)
    public String legacyType;

    /** Whether it wants an item of this type — a notification type's name, or {@link #TASK} — for this hotel. */
    public boolean wants(String type, String hotel) {
        if (!active || !(hotelCode == null || hotelCode.isBlank() || hotelCode.equals(hotel))) {
            return false;
        }
        if (TASK.equals(type)) {
            return tasks;
        }
        var wanted = typeList();
        return wanted.isEmpty() || wanted.contains(type);
    }

    public Set<Channel> channelSet() {
        var set = EnumSet.noneOf(Channel.class);
        for (var name : split(channels)) {
            Arrays.stream(Channel.values()).filter(c -> c.name().equalsIgnoreCase(name)).forEach(set::add);
        }
        return set.isEmpty() ? EnumSet.of(Channel.EMAIL) : set;
    }

    public boolean by(Channel channel) {
        return channelSet().contains(channel);
    }

    /** The spaces it names that exist, out of the {@code configured} ones; none named is all of them. */
    public List<Integer> spaces(int configured) {
        var named = split(chatSpaces).stream().map(Recipient::number).filter(Objects::nonNull)
                .filter(n -> n >= 1 && n <= configured).distinct().toList();
        return named.isEmpty() && split(chatSpaces).isEmpty() ? IntStream.rangeClosed(1, configured).boxed().toList() : named;
    }

    public List<String> typeList() {
        return split(types);
    }

    public List<String> userList() {
        return split(users);
    }

    public List<String> roleList() {
        return split(roles);
    }

    /** Whether this person — by username, or by one of their roles — is one of its people. */
    public boolean is(String username, Collection<String> theirRoles) {
        return (username != null && userList().contains(username))
                || (theirRoles != null && roleList().stream().anyMatch(theirRoles::contains));
    }

    public static String join(Collection<?> values) {
        return values == null || values.isEmpty() ? null
                : values.stream().map(v -> v instanceof Enum<?> e ? e.name() : String.valueOf(v)).reduce((a, b) -> a + "," + b).orElse(null);
    }

    public static List<String> split(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    static Integer number(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
