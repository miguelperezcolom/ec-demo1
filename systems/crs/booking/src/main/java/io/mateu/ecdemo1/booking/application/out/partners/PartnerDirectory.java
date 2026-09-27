package io.mateu.ecdemo1.booking.application.out.partners;

import java.util.List;

/**
 * The trading partners — tour operators, agencies, companies — as the ERP's master of partners
 * knows them. The CRS does not keep its own list: a booking names a partner by the master's code.
 */
public interface PartnerDirectory {

    /** The partners that can sell today: the active ones. */
    List<TradingPartner> activePartners();

    /**
     * @param type the master's partner type: TravelAgent, TourOperator, OnlineAgency or Company
     */
    record TradingPartner(String code, String type, String name) {
    }
}
