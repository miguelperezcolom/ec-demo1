package io.mateu.ecdemo1.partners.domain.partner;

/**
 * Which profile the partner is in the PMS (Opera): what a reservation of it is attached to. Known,
 * the partner needs no creating there when a reservation uses it.
 *
 * @param profileId   Opera's profile id
 * @param profileType Opera's profile type: Agent, Company or Source
 */
public record PmsProfile(String profileId, String profileType) {

    public PmsProfile {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("A PMS profile needs its id");
        }
    }
}
