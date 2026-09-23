package io.mateu.ecdemo1.mdm.salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.config.TolerantReader;
import io.mateu.ecdemo1.mdm.store.Customer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Salesforce's REST API, in the MDM's terms — the anti-corruption layer of the HLA's
 * {@code salesforce-adapter}. Machine to machine: a client-credentials token, fetched when needed and
 * again when Salesforce stops accepting it.
 */
@Component
@Slf4j
public class SalesforceClient {

    /** What a token gives: where the org answers, and who it is. */
    public record Session(String instanceUrl, String accessToken, String orgId, String userId) {
    }

    final MdmProperties.Salesforce properties;
    final RestClient rest;
    volatile Session session;

    public SalesforceClient(MdmProperties properties, TolerantReader reader) {
        this.properties = properties.salesforce();
        this.rest = RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public synchronized Session session() {
        if (session == null) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            var domain = properties.domain().startsWith("http") ? properties.domain() : "https://" + properties.domain();
            var token = rest.post().uri(domain + "/services/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            var identity = token.path("id").asText().split("/");
            session = new Session(token.path("instance_url").asText(), token.path("access_token").asText(),
                    identity[identity.length - 2], identity[identity.length - 1]);
            log.info("Salesforce session for org {} as user {}", session.orgId(), session.userId());
        }
        return session;
    }

    /** Forgets the token: the next call asks for a new one. */
    public synchronized void expire() {
        session = null;
    }

    /**
     * Creates or updates the customer's contact, by its MDM id (the external id field). Duplicates
     * are saved, not refused: finding them is what Salesforce is for, and a steward merges them.
     */
    public String upsertContact(Customer c) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("FirstName", c.firstName);
        fields.put("LastName", c.lastName == null || c.lastName.isBlank() ? "?" : c.lastName);
        fields.put("Email", c.email);
        fields.put("Phone", c.phone);
        fields.put("Birthdate", c.birthDate == null ? null : c.birthDate.toString());
        fields.put("Nationality__c", c.nationality);
        fields.put("Document_Type__c", c.documentType);
        fields.put("Document_Number__c", c.documentNumber);
        var answer = call(s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/sobjects/Contact/MDM_Id__c/{id}", properties.apiVersion(), c.id)
                .header("Authorization", "Bearer " + s.accessToken())
                .header("Sforce-Duplicate-Rule-Header", "allowSave=true")
                .contentType(MediaType.APPLICATION_JSON).body(fields)
                .retrieve().body(JsonNode.class));
        return answer == null ? null : answer.path("id").asText(null);
    }

    /** SOQL over live and deleted records alike: a merge's absorbed contact is only in the latter. */
    public List<JsonNode> queryAll(String soql) {
        var records = new ArrayList<JsonNode>();
        var page = call(s -> rest.get()
                .uri(s.instanceUrl() + "/services/data/{v}/queryAll?q={q}", properties.apiVersion(), soql)
                .header("Authorization", "Bearer " + s.accessToken()).retrieve().body(JsonNode.class));
        while (true) {
            page.path("records").forEach(records::add);
            var next = page.path("nextRecordsUrl").asText(null);
            if (next == null || page.path("done").asBoolean(true)) {
                return records;
            }
            page = call(s -> rest.get().uri(s.instanceUrl() + next)
                    .header("Authorization", "Bearer " + s.accessToken()).retrieve().body(JsonNode.class));
        }
    }

    public Optional<JsonNode> contact(String contactId) {
        var found = queryAll(("SELECT Id, IsDeleted, MasterRecordId, MDM_Id__c, FirstName, LastName, Email, Phone, Birthdate, "
                + "Nationality__c, Document_Type__c, Document_Number__c FROM Contact WHERE Id = '%s'").formatted(safe(contactId)));
        return found.stream().findFirst();
    }

    /** Gives a contact the MDM id of the customer it now stands for. */
    public void assignMdmId(String contactId, String mdmId) {
        call(s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/sobjects/Contact/{id}", properties.apiVersion(), contactId)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("MDM_Id__c", mdmId))
                .retrieve().toBodilessEntity());
    }

    /** Record ids are fifteen or eighteen letters and digits; anything else is not put in a query. */
    static String safe(String id) {
        if (id == null || !id.matches("[A-Za-z0-9]{15,18}")) {
            throw new IllegalArgumentException("Not a Salesforce id: " + id);
        }
        return id;
    }

    <T> T call(Function<Session, T> request) {
        try {
            return request.apply(session());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw e;
            }
            expire();
            return request.apply(session());
        }
    }
}
