package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The hotels this front office serves — the options of the suite's {@code Hotel} selector
 * ({@code @AppContext}) — and which one the desk has chosen.
 *
 * <p>Today the service is bound to one property by configuration ({@code frontoffice.hotel}, the
 * chain's code, and {@code frontoffice.pms-hotel}, the PMS property whose stays and catalogue it
 * receives — MRU01 ↔ Opera XMAR): {@code PmsStays} drops what comes from any other, so every stay in
 * its database is that hotel's. The selector therefore offers that one hotel, and a choice that is not
 * one of the served hotels (nothing chosen yet, or a value left in the browser from another build)
 * falls back to the first. The screens ask {@link #holdsStays} before showing stays, so a second hotel
 * only has to make the stays carry their hotel.
 */
@Service
public class Hotels implements LookupOptionsSupplier {

  /** The {@code @AppContext} field's name in {@code FrontOfficeSuite}: the key of the app state. */
  public static final String CONTEXT_FIELD = "hotel";

  /** A hotel of the chain as the front office serves it. */
  public record Hotel(String code, String pmsCode, String name) {

    /** What the selector shows: the hotel's name and its code. */
    public String label() {
      return name == null || name.isBlank() || name.equals(code) ? code : name + " · " + code;
    }
  }

  private static Hotels instance;

  private final Hotel configured;

  public Hotels(@Value("${frontoffice.hotel:MRU01}") String hotel,
                @Value("${frontoffice.pms-hotel:XMAR}") String pmsHotel,
                @Value("${frontoffice.hotel-name:}") String name) {
    this.configured = new Hotel(hotel, pmsHotel, name == null || name.isBlank() ? hotel : name);
    instance = this;
  }

  /** For the view models Mateu builds itself (the welcome page's field initializers). */
  public static Hotels instance() {
    return instance;
  }

  /** The hotels served, the default first. */
  public List<Hotel> served() {
    return List.of(configured);
  }

  /** The hotel the desk has chosen in the selector; the first served one when there is no valid choice. */
  public Hotel selected(HttpRequest httpRequest) {
    var chosen = chosen(httpRequest);
    return served().stream()
        .filter(h -> h.code().equalsIgnoreCase(chosen))
        .findFirst()
        .orElse(served().get(0));
  }

  /** Whether this front office's stays are the given hotel's: they all are the configured hotel's. */
  public boolean holdsStays(Hotel hotel) {
    return hotel != null && configured.code().equals(hotel.code());
  }

  /** Whether the stays shown should be this database's, for the hotel chosen in the request. */
  public boolean holdsStaysOfSelected(HttpRequest httpRequest) {
    return holdsStays(selected(httpRequest));
  }

  @Override
  public ListingData<Option> search(String fieldName, String searchText, Pageable pageable,
                                    HttpRequest httpRequest) {
    var s = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
    return ListingData.of(served().stream()
        .filter(h -> s.isEmpty() || h.label().toLowerCase(Locale.ROOT).contains(s))
        .map(h -> new Option(h.code(), h.label()))
        .toList());
  }

  private static String chosen(HttpRequest httpRequest) {
    if (httpRequest == null) {
      return null;
    }
    try {
      var value = httpRequest.appContext(CONTEXT_FIELD);
      return value == null ? null : String.valueOf(value).trim();
    } catch (RuntimeException e) {
      // a request without the action's body (a plain GET): no choice made
      return null;
    }
  }
}
