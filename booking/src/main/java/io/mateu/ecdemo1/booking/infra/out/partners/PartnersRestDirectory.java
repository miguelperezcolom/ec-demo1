package io.mateu.ecdemo1.booking.infra.out.partners;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.booking.application.out.partners.PartnerDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** Reads the partners from the ERP's master of partners, {@code GET /partners}, a page at a time. */
@Component
public class PartnersRestDirectory implements PartnerDirectory {

    static final int PAGE_SIZE = 200;
    /** A bound, not a limit anyone should reach: the master holds a few hundred partners. */
    static final int MAX_PAGES = 20;

    final RestClient client;

    public PartnersRestDirectory(@Value("${partners.url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartnerView(String code, String type, String name, boolean active) {
    }

    @Override
    public List<TradingPartner> activePartners() {
        var partners = new ArrayList<TradingPartner>();
        for (int page = 0; page < MAX_PAGES; page++) {
            var pageNumber = page;
            var views = client.get()
                    .uri(b -> b.path("/partners").queryParam("page", pageNumber).queryParam("size", PAGE_SIZE).build())
                    .retrieve().body(PartnerView[].class);
            if (views == null) {
                break;
            }
            for (var view : views) {
                if (view.active() && view.code() != null) {
                    partners.add(new TradingPartner(view.code(), view.type(), view.name()));
                }
            }
            if (views.length < PAGE_SIZE) {
                break;
            }
        }
        return partners;
    }
}
