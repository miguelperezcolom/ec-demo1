package io.mateu.ecdemo1.integrations.outbox;

/**
 * A command to another service over HTTP that must happen only if the decision that asked for it
 * was saved — and then surely. Written to the {@link RemoteCalls} outbox in the decision's
 * transaction, sent after it commits. Every one is idempotent on the other side: sent twice, it
 * changes nothing the second time.
 */
public sealed interface RemoteCall {

    /** The hotel's own equivalence in the mapping — which Opera property the CRS hotel is. */
    record DefineHotel(String crsHotelCode, String pmsHotelCode, String by) implements RemoteCall {
    }

    /** Which Opera profile type each partner type is, in the mapping. */
    record DefinePartnerTypes(String by) implements RemoteCall {
    }

    /** Asks the mapping agent to propose the hotel's pending codes. */
    record RequestAgentProposal(String crsHotelCode) implements RemoteCall {
    }

    /** Announces a partner again in the master of partners, so that it is projected to the PMS. */
    record ResyncPartner(String partnerCode) implements RemoteCall {
    }

    /** Resolves a cause in the mapping, if it is open: what held the processes waiting on it goes on. */
    record ResolveCause(String causeKey, String by) implements RemoteCall {
    }
}
