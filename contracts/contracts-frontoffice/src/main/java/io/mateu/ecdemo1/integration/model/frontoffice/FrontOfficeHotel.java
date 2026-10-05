package io.mateu.ecdemo1.integration.model.frontoffice;

/**
 * A hotel a front office serves, as it tells whoever asks ({@code GET /api/hotels}): the pms-fo
 * integration offers these when it is registered, and refuses a front office code that is not one of them.
 *
 * @param code         the hotel's code in the chain, as the front office names itself (MRU01)
 * @param name         what the front office calls it
 * @param pmsHotelCode the PMS property whose stays and catalogue it takes (Opera XMAR); anything of
 *                     another property is dropped
 */
public record FrontOfficeHotel(String code, String name, String pmsHotelCode) {
}
