package io.mateu.ecdemo1.mdm.salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
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

    /** The most records one sObject Collections call takes. */
    public static final int COLLECTION = 200;

    /**
     * What a call is for — the tag every call is counted under ({@link ApiCalls}), so the org's
     * allowance can be told apart by who spent it.
     */
    public static final class Purpose {
        public static final String PROJECTION = "projection";
        public static final String CHANGE_CASE = "change-case";
        public static final String DECISIONS = "decisions";
        public static final String REASON = "reason";
        public static final String REFRESH = "refresh";
        public static final String POLL = "poll";
        public static final String MERGE = "merge";
        public static final String CONSOLIDATION_READ = "consolidation-read";
        public static final String CONSOLIDATION_WRITE = "consolidation-write";
        public static final String LIMITS = "limits";
        /** An OAuth token: counted, but not a call the org's allowance counts. */
        public static final String TOKEN = "token";

        private Purpose() {
        }
    }

    /**
     * Salesforce refuses for the daily API allowance, or the MDM is not asking while it recovers
     * ({@link SalesforceBudget}): nothing is wrong with what was sent — it waits, and goes later.
     */
    public static class LimitExceeded extends RuntimeException {
        public LimitExceeded(String message) {
            super(message);
        }
    }

    /** One record of a collection's answer: its contact, or why Salesforce refused it. */
    public record Upserted(String mdmId, String contactId, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    final MdmProperties.Salesforce properties;
    final SalesforceBudget budget;
    final ApiCalls calls;
    final RestClient rest;
    volatile Session session;

    @org.springframework.beans.factory.annotation.Autowired
    public SalesforceClient(MdmProperties properties, TolerantReader reader, SalesforceBudget budget, ApiCalls calls) {
        this(properties, reader, budget, calls, RestClient.builder());
    }

    SalesforceClient(MdmProperties properties, TolerantReader reader, SalesforceBudget budget, ApiCalls calls,
                     RestClient.Builder builder) {
        this.properties = properties.salesforce();
        this.budget = budget;
        this.calls = calls;
        this.rest = builder
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                // Every answer says how much of the day's allowance is used: kept, for free.
                .requestInterceptor((request, body, execution) -> {
                    var response = execution.execute(request, body);
                    budget.observe(response.getHeaders().getFirst("Sforce-Limit-Info"));
                    return response;
                })
                .build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Configured, and not pausing for the daily allowance: whether it is worth asking now. */
    public boolean available() {
        return enabled() && budget.open();
    }

    public SalesforceBudget budget() {
        return budget;
    }

    public ApiCalls calls() {
        return calls;
    }

    public synchronized Session session() {
        if (session == null) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            var domain = properties.domain().startsWith("http") ? properties.domain() : "https://" + properties.domain();
            JsonNode token;
            try {
                token = rest.post().uri(domain + "/services/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
                calls.record(Purpose.TOKEN, ApiCalls.Outcome.OK);
            } catch (RuntimeException e) {
                calls.record(Purpose.TOKEN, ApiCalls.Outcome.ERROR);
                throw e;
            }
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
        var fields = contactFields(c);
        var answer = call(Purpose.PROJECTION, s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/sobjects/Contact/MDM_Id__c/{id}", properties.apiVersion(), c.id)
                .header("Authorization", "Bearer " + s.accessToken())
                .header("Sforce-Duplicate-Rule-Header", "allowSave=true")
                .contentType(MediaType.APPLICATION_JSON).body(fields)
                .retrieve().body(JsonNode.class));
        return answer == null ? null : answer.path("id").asText(null);
    }

    /**
     * {@link #upsertContact} for up to {@value #COLLECTION} customers in one call — sObject
     * Collections' upsert by the external id, not all or none: one Salesforce refuses does not keep the
     * others back. One call, not one per customer, out of the org's daily allowance. The answer is in
     * the order sent.
     */
    public List<Upserted> upsertContacts(List<Customer> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        if (customers.size() > COLLECTION) {
            throw new IllegalArgumentException("At most " + COLLECTION + " contacts in one call, not " + customers.size());
        }
        var records = new ArrayList<Map<String, Object>>();
        for (var c : customers) {
            var record = new LinkedHashMap<String, Object>();
            record.put("attributes", Map.of("type", "Contact"));
            record.put("MDM_Id__c", c.id);
            record.putAll(contactFields(c));
            records.add(record);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("allOrNone", false);
        body.put("records", records);
        var answer = call(Purpose.PROJECTION, s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/composite/sobjects/Contact/MDM_Id__c", properties.apiVersion())
                .header("Authorization", "Bearer " + s.accessToken())
                .header("Sforce-Duplicate-Rule-Header", "allowSave=true")
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class));
        return upserted(customers, answer);
    }

    static List<Upserted> upserted(List<Customer> sent, JsonNode answer) {
        var result = new ArrayList<Upserted>();
        for (int i = 0; i < sent.size(); i++) {
            var r = answer == null ? null : answer.get(i);
            var id = sent.get(i).id;
            if (r == null) {
                result.add(new Upserted(id, null, "no answer for this record"));
            } else if (r.path("success").asBoolean(false)) {
                result.add(new Upserted(id, r.path("id").asText(null), null));
            } else {
                var errors = new ArrayList<String>();
                r.path("errors").forEach(e -> errors.add(e.path("statusCode").asText("") + " " + e.path("message").asText("")));
                result.add(new Upserted(id, null, errors.isEmpty() ? "refused" : String.join("; ", errors).trim()));
            }
        }
        return result;
    }

    static LinkedHashMap<String, Object> contactFields(Customer c) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("FirstName", c.firstName);
        fields.put("LastName", c.lastName == null || c.lastName.isBlank() ? "?" : c.lastName);
        fields.put("Email", c.email);
        fields.put("Phone", c.phone);
        fields.put("Birthdate", c.birthDate == null ? null : c.birthDate.toString());
        fields.put("Nationality__c", c.nationality);
        fields.put("Document_Type__c", c.documentType);
        fields.put("Document_Number__c", c.documentNumber);
        return fields;
    }

    /**
     * SOQL over live and deleted records alike: a merge's absorbed contact is only in the latter. One
     * call per page, each counted under the purpose.
     */
    public List<JsonNode> queryAll(String purpose, String soql) {
        var records = new ArrayList<JsonNode>();
        var page = call(purpose, s -> rest.get()
                .uri(s.instanceUrl() + "/services/data/{v}/queryAll?q={q}", properties.apiVersion(), soql)
                .header("Authorization", "Bearer " + s.accessToken()).retrieve().body(JsonNode.class));
        while (true) {
            page.path("records").forEach(records::add);
            var next = page.path("nextRecordsUrl").asText(null);
            if (next == null || page.path("done").asBoolean(true)) {
                return records;
            }
            page = call(purpose, s -> rest.get().uri(s.instanceUrl() + next)
                    .header("Authorization", "Bearer " + s.accessToken()).retrieve().body(JsonNode.class));
        }
    }

    /**
     * The change request as a Case on the contact, for someone to decide in Salesforce: Decisión
     * Pendiente, the proposed data in its fields. Keyed by the request's id, so sending it twice is
     * one Case.
     */
    public String upsertChangeCase(io.mateu.ecdemo1.mdm.store.ChangeRequest r, String contactId, String subject, String description) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("Subject", subject);
        fields.put("Description", description);
        fields.put("ContactId", contactId);
        fields.put("MdmId__c", r.customerId);
        fields.put("Nombre__c", r.firstName);
        fields.put("Apellidos__c", r.lastName == null || r.lastName.isBlank() ? "?" : r.lastName);
        fields.put("Email__c", r.email);
        fields.put("Telefono__c", r.phone);
        fields.put("Nacionalidad__c", r.nationality);
        fields.put("FechaNacimiento__c", r.birthDate == null ? null : r.birthDate.toString());
        fields.put("TipoDocumento__c", r.documentType);
        fields.put("NumeroDocumento__c", r.documentNumber);
        fields.put("Origen__c", cut(r.origin, 255));
        fields.put("Cambios__c", cut(r.changes, 255));
        fields.put("Decision__c", "Pendiente");
        var answer = call(Purpose.CHANGE_CASE, s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/sobjects/Case/MdmRequestId__c/{id}", properties.apiVersion(), r.id)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(fields)
                .retrieve().body(JsonNode.class));
        return answer == null ? null : answer.path("id").asText(null);
    }

    /** The decided ones among these change requests: request id → Aprobada or Rechazada. */
    public java.util.Map<String, String> decisions(java.util.Collection<String> requestIds) {
        var decided = new java.util.HashMap<String, String>();
        if (requestIds.isEmpty()) {
            return decided;
        }
        var in = requestIds.stream().map(id -> "'" + literal(id) + "'").collect(java.util.stream.Collectors.joining(","));
        for (var c : queryAll(Purpose.DECISIONS, "SELECT MdmRequestId__c, Decision__c FROM Case WHERE MdmRequestId__c IN (" + in + ")")) {
            var decision = c.path("Decision__c").asText("");
            if ("Aprobada".equals(decision) || "Rechazada".equals(decision)) {
                decided.put(c.path("MdmRequestId__c").asText(), decision);
            }
        }
        return decided;
    }

    /** Why a change request was rejected, as its Case says (Motivo), if it says. */
    public Optional<String> reason(String requestId) {
        return queryAll(Purpose.REASON, "SELECT Motivo__c FROM Case WHERE MdmRequestId__c = '%s'".formatted(literal(requestId))).stream()
                .map(c -> c.path("Motivo__c"))
                .filter(m -> !m.isMissingNode() && !m.isNull() && !m.asText().isBlank())
                .map(m -> m.asText().trim())
                .findFirst();
    }

    /** The contact that carries this MDM id, as Salesforce has it now. */
    public Optional<JsonNode> contactByMdmId(String mdmId) {
        return queryAll(Purpose.REFRESH, ("SELECT Id, IsDeleted, MasterRecordId, MDM_Id__c, FirstName, LastName, Email, Phone, Birthdate, "
                + "Nationality__c, Document_Type__c, Document_Number__c FROM Contact WHERE MDM_Id__c = '%s' AND IsDeleted = false")
                .formatted(literal(mdmId))).stream().findFirst();
    }

    public Optional<JsonNode> contact(String contactId) {
        var found = queryAll(Purpose.CONSOLIDATION_READ, ("SELECT Id, IsDeleted, MasterRecordId, MDM_Id__c, FirstName, LastName, Email, Phone, Birthdate, "
                + "Nationality__c, Document_Type__c, Document_Number__c FROM Contact WHERE Id = '%s'").formatted(safe(contactId)));
        return found.stream().findFirst();
    }

    /** Gives a contact the MDM id of the customer it now stands for. */
    public void assignMdmId(String contactId, String mdmId) {
        call(Purpose.CONSOLIDATION_WRITE, s -> rest.patch()
                .uri(s.instanceUrl() + "/services/data/{v}/sobjects/Contact/{id}", properties.apiVersion(), contactId)
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("MDM_Id__c", mdmId))
                .retrieve().toBodilessEntity());
    }

    /** Salesforce refused a merge, and would again: said why, not retried. */
    public static class MergeRefused extends RuntimeException {
        public MergeRefused(String message) {
            super(message);
        }
    }

    /**
     * Merges a contact into another, as a steward would: the absorbed one goes to the recycle bin with
     * {@code MasterRecordId} naming the master, its Cases move to the master, and the master keeps its
     * own values. Salesforce's REST API has no merge — this is the SOAP API's {@code merge()}, the one
     * {@code salesforce/dedup.py} uses on this org (Base Edition has the SOAP API). The flow
     * {@code Mdm_Announce_Merge} then announces it (ClienteConsolidado__e), as it does any merge.
     *
     * @throws MergeRefused when Salesforce answers it will not (a contact that is gone, one already merged)
     */
    public void mergeContacts(String masterContactId, String absorbedContactId) {
        var master = safe(masterContactId);
        var absorbed = safe(absorbedContactId);
        var version = properties.apiVersion().startsWith("v") ? properties.apiVersion().substring(1) : properties.apiVersion();
        java.util.function.Function<Session, String> merge = s -> rest.post()
                .uri(s.instanceUrl() + "/services/Soap/u/" + version)
                .contentType(MediaType.parseMediaType("text/xml; charset=UTF-8"))
                .header("SOAPAction", "\"\"")
                .body(mergeEnvelope(s.accessToken(), master, absorbed))
                .exchange((request, response) -> new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        ensureOpen();
        var outcome = merged(merge);
        if (outcome != null && outcome.contains("INVALID_SESSION_ID")) {
            expire();
            outcome = merged(merge);
        }
        if (SalesforceBudget.isLimit(outcome)) {
            budget.exceeded(outcome);
            throw new LimitExceeded(outcome);
        }
        budget.succeeded();
        if (outcome != null) {
            throw new MergeRefused(outcome);
        }
    }

    /** One SOAP merge call, counted: null if it merged, else what Salesforce said. */
    String merged(Function<Session, String> merge) {
        String outcome;
        try {
            outcome = mergeOutcome(merge.apply(session()));
        } catch (RuntimeException e) {
            calls.record(Purpose.MERGE, ApiCalls.Outcome.ERROR);
            throw e;
        }
        calls.record(Purpose.MERGE, outcome == null ? ApiCalls.Outcome.OK
                : SalesforceBudget.isLimit(outcome) ? ApiCalls.Outcome.LIMITED : ApiCalls.Outcome.ERROR);
        return outcome;
    }

    /**
     * Asks the org how much of its daily allowance is used — one call, itself counted. Every answer
     * already says it in a header; this is for when nothing has been asked for a while.
     */
    public void refreshLimits() {
        var limits = call(Purpose.LIMITS, s -> rest.get()
                .uri(s.instanceUrl() + "/services/data/{v}/limits", properties.apiVersion())
                .header("Authorization", "Bearer " + s.accessToken()).retrieve().body(JsonNode.class));
        var daily = limits == null ? null : limits.path("DailyApiRequests");
        if (daily != null && daily.has("Max") && daily.has("Remaining")) {
            var max = daily.path("Max").asLong();
            budget.observed(max - daily.path("Remaining").asLong(), max);
        }
    }

    static String mergeEnvelope(String sessionId, String master, String absorbed) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:urn=\"urn:partner.soap.sforce.com\" xmlns:sobj=\"urn:sobject.partner.soap.sforce.com\">"
                + "<soapenv:Header><urn:SessionHeader><urn:sessionId>" + xml(sessionId) + "</urn:sessionId></urn:SessionHeader></soapenv:Header>"
                + "<soapenv:Body><urn:merge><urn:request><urn:masterRecord><sobj:type>Contact</sobj:type><sobj:Id>" + master
                + "</sobj:Id></urn:masterRecord><urn:recordToMergeIds>" + absorbed + "</urn:recordToMergeIds>"
                + "</urn:request></urn:merge></soapenv:Body></soapenv:Envelope>";
    }

    /** Null if the merge succeeded; otherwise what Salesforce said (a fault, or the result's errors). */
    static String mergeOutcome(String answer) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(answer)));
            var fault = doc.getElementsByTagNameNS("*", "Fault");
            if (fault.getLength() > 0) {
                return text(doc, "faultcode") + ": " + text(doc, "faultstring");
            }
            if ("true".equals(text(doc, "success"))) {
                return null;
            }
            var errors = doc.getElementsByTagNameNS("*", "errors");
            var said = new ArrayList<String>();
            for (int i = 0; i < errors.getLength(); i++) {
                said.add(errors.item(i).getTextContent().trim().replaceAll("\\s+", " "));
            }
            return said.isEmpty() ? "merge not done: " + cut(answer, 300) : String.join("; ", said);
        } catch (Exception e) {
            return "unreadable answer: " + cut(answer, 300);
        }
    }

    static String text(org.w3c.dom.Document doc, String localName) {
        var nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName(localName);
        }
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Record ids are fifteen or eighteen letters and digits; anything else is not put in a query. */
    /** A value inside a SOQL string literal: quotes and backslashes escaped. */
    static String literal(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    static String cut(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }

    static String safe(String id) {
        if (id == null || !id.matches("[A-Za-z0-9]{15,18}")) {
            throw new IllegalArgumentException("Not a Salesforce id: " + id);
        }
        return id;
    }

    /**
     * One call, with a new token if Salesforce stopped taking the one it had. Not made while the
     * allowance pauses calls; an allowance refusal starts or lengthens the pause.
     */
    <T> T call(String purpose, Function<Session, T> request) {
        ensureOpen();
        try {
            T answer;
            try {
                answer = counted(purpose, request);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                    throw e;
                }
                expire();
                answer = counted(purpose, request);
            }
            budget.succeeded();
            return answer;
        } catch (HttpClientErrorException e) {
            var said = e.getResponseBodyAsString();
            if (SalesforceBudget.isLimit(said)) {
                budget.exceeded(said);
                throw new LimitExceeded(e.getStatusCode().value() + " " + said);
            }
            // Salesforce answered: it is up, whatever it thought of this one.
            budget.succeeded();
            throw e;
        }
    }

    /** One call, counted under its purpose with how it went. */
    <T> T counted(String purpose, Function<Session, T> request) {
        var session = session();
        try {
            T answer = request.apply(session);
            calls.record(purpose, ApiCalls.Outcome.OK);
            return answer;
        } catch (HttpClientErrorException e) {
            calls.record(purpose, SalesforceBudget.isLimit(e.getResponseBodyAsString())
                    ? ApiCalls.Outcome.LIMITED : ApiCalls.Outcome.ERROR);
            throw e;
        } catch (RuntimeException e) {
            calls.record(purpose, ApiCalls.Outcome.ERROR);
            throw e;
        }
    }

    void ensureOpen() {
        if (!budget.open()) {
            throw new LimitExceeded("Salesforce's daily API allowance is spent: not asking until " + budget.pausedUntil());
        }
    }
}
