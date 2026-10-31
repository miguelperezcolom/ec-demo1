package io.mateu.ecdemo1.contenthub.domain.shared;

/** The events that cross aggregate boundaries (spec: "Eventos que cruzan límites"). */
public final class ContentEvents {

    private ContentEvents() {
    }

    public record ItemPublished(String itemId, int version) implements DomainEvent {
        public String subject() {
            return itemId;
        }
    }

    public record ItemRetired(String itemId) implements DomainEvent {
        public String subject() {
            return itemId;
        }
    }

    /**
     * A publication date was crossed. Only for caches and other systems: visibility never waits for it,
     * the instant is filtered in every query.
     */
    public record ItemBecameEffective(String itemId) implements DomainEvent {
        public String subject() {
            return itemId;
        }
    }

    /** An expiry date was crossed. As {@link ItemBecameEffective}: caches and others only. */
    public record ItemExpired(String itemId) implements DomainEvent {
        public String subject() {
            return itemId;
        }
    }

    public record AudienceRedefined(String audienceId) implements DomainEvent {
        public String subject() {
            return audienceId;
        }
    }

    public record SlotVariantPublished(String slotKey) implements DomainEvent {
        public String subject() {
            return slotKey;
        }
    }

    public record NavigationChanged() implements DomainEvent {
        public String subject() {
            return "navigation";
        }
    }

    /** An employee was added, changed attributes, or left — from the users service's directory. */
    public record EmployeeChanged(String userId, String oldHash, String newHash) implements DomainEvent {
        public String subject() {
            return userId;
        }
    }
}
