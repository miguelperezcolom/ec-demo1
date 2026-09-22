package io.mateu.ecdemo1.crsintegration.source;

import io.mateu.ecdemo1.crsintegration.config.CrsProperties;
import io.mateu.ecdemo1.crsintegration.config.TolerantReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Reads from and writes to the CRS side: the booking API and the master of partners. The one place
 * in the integration that knows their URLs and their shapes.
 */
@Component
public class CrsSource {

    final RestClient booking;
    final RestClient partners;

    public CrsSource(CrsProperties properties, TolerantReader reader) {
        this.booking = client(properties.bookingUrl(), reader);
        this.partners = client(properties.partnersUrl(), reader);
    }

    private static RestClient client(String baseUrl, TolerantReader reader) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    /** Empty when the booking does not exist — deleted, or never saved. */
    public Optional<BookingView> booking(String id) {
        try {
            return Optional.ofNullable(booking.get().uri("/bookings/{id}", id).retrieve().body(BookingView.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public Optional<PartnerView> partner(String code) {
        try {
            return Optional.ofNullable(partners.get().uri("/partners/{code}", code).retrieve().body(PartnerView.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public CatalogView catalog() {
        return booking.get().uri("/catalog").retrieve().body(CatalogView.class);
    }

    /** Records in the CRS where the booking landed in the PMS, so an operator of the CRS can find it. */
    public void annotatePmsReference(String bookingId, String pmsReservationId) {
        booking.put().uri("/bookings/{id}/pms-reference", bookingId)
                .body(Map.of("reservationId", pmsReservationId))
                .retrieve().toBodilessEntity();
    }
}
