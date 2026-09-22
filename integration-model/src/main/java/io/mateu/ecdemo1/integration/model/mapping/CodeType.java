package io.mateu.ecdemo1.integration.model.mapping;

/**
 * The kinds of code the mapping translates from the CRS's catalog to the PMS's. HOTEL is always a
 * chain-level entry; the rest can be chain-level with exceptions per property.
 */
public enum CodeType {
    HOTEL, ROOM_TYPE, RATE_PLAN, BOARD, CHANNEL, PAYMENT_METHOD, CANCELLATION_REASON, PARTNER_TYPE
}
