package io.mateu.ecdemo1.integration.model.mapping;

/**
 * The kinds of code the mapping translates from the CRS's catalog to the PMS's. HOTEL is always a
 * chain-level entry; the rest can be chain-level with exceptions per property.
 *
 * <p>MARKET appears only in a PMS catalog: a CRS channel is, in Opera, a source code and a market
 * code, so a CHANNEL maps to a source code with the market code as an attribute — and whoever
 * proposes it needs to see the market codes there are.
 */
public enum CodeType {
    HOTEL, ROOM_TYPE, RATE_PLAN, BOARD, CHANNEL, PAYMENT_METHOD, CANCELLATION_REASON, PARTNER_TYPE, MARKET
}
