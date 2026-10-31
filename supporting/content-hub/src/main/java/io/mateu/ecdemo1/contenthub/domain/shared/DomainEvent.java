package io.mateu.ecdemo1.contenthub.domain.shared;

/**
 * Something that happened to an aggregate that someone outside it reacts to: the visibility projection,
 * the caches, the search index, the newsletter — and, through the outbox, other systems.
 */
public sealed interface DomainEvent permits ContentEvents.ItemPublished, ContentEvents.ItemRetired,
        ContentEvents.ItemBecameEffective, ContentEvents.ItemExpired, ContentEvents.AudienceRedefined,
        ContentEvents.SlotVariantPublished, ContentEvents.NavigationChanged, ContentEvents.EmployeeChanged {

    /** What the event is about: an item id, an audience id, a slot key… */
    String subject();
}
