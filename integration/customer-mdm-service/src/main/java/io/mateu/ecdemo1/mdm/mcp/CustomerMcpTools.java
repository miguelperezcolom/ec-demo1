package io.mateu.ecdemo1.mdm.mcp;

import io.mateu.ecdemo1.mdm.application.IdentityLookup;
import io.mateu.ecdemo1.mdm.rest.CustomerController;
import io.mateu.ecdemo1.mdm.rest.CustomerView;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The golden records as tools for an agent: read-only. Who a customer is changes by reservations
 * and by the merges stewards make in Salesforce — never from a chat.
 */
@Component
@RequiredArgsConstructor
public class CustomerMcpTools {

    final CustomerController api;
    final IdentityLookup lookup;

    /**
     * What a lookup by document answered. A record, not Object: Spring AI drops a tool whose return type
     * is Object without a word.
     *
     * @param answer   the customer found, or why there is none — nobody, or several (whose data are not shown)
     * @param customer the customer, when there is exactly one
     */
    public record DocumentLookup(String answer, IdentityLookup.Found customer) {
    }

    public String getSystemContext() {
        return """
                Maestro de clientes (MDM) — la vista única del cliente:
                - Cada pasajero de una reserva se resuelve a un cliente (golden record) antes de llegar a Opera. Si
                  no hay coincidencia segura (documento, o email con el mismo nombre) se crea un cliente PROVISIONAL:
                  la venta nunca espera a la limpieza.
                - Los provisionales se envían a Salesforce, que detecta duplicados; un data steward los fusiona allí.
                  El MDM recibe la fusión y decide la supervivencia: el absorbido queda como alias (MERGED) del
                  superviviente (CONSOLIDATED), y sus reservas se proyectan de nuevo con el código superviviente.
                - Un cliente puede tener varios documentos (DNI, pasaporte...): un documento es su número y el país
                  que lo emite. Si varios clientes tienen el mismo documento, solo se dice cuántos, sin sus datos.
                - Salesforce limpia; el maestro es el MDM. Estas herramientas solo consultan: fusionar se hace en
                  Salesforce, no desde el chat.
                - Son datos personales: muestra solo lo que el usuario necesite para su pregunta.
                """;
    }

    @Tool(description = "Customers (golden records) whose name, email, document or code contains the text; absorbed ones are left out")
    public List<CustomerView> findCustomers(@ToolParam(description = "Text to look for; empty for the most recently changed") String text) {
        return api.search(text == null ? "" : text, null);
    }

    @Tool(description = "The customer known by a reference in another system: its Salesforce contact (SALESFORCE), its guest in a front office (FRONT_OFFICE), its guest profile in Opera (OPERA) or its Riu Class membership number (RIU_CLASS)")
    public List<CustomerView> findCustomerByXref(@ToolParam(description = "SALESFORCE, FRONT_OFFICE, OPERA or RIU_CLASS") String system,
                                                 @ToolParam(description = "The reference in that system, e.g. an Opera profile id") String reference) {
        return api.byXref(system + ":" + reference);
    }

    @Tool(description = "The customer an identity document (DNI, passport...) is, among every document the MDM knows of each customer. "
            + "Only one customer is ever shown: if several hold the number, it says how many and nothing more")
    public DocumentLookup findCustomerByDocument(
            @ToolParam(description = "The document number, however written") String documentNumber,
            @ToolParam(description = "The issuing country, ISO-2 (ES, DE...); empty if unknown", required = false) String country) {
        return switch (lookup.lookup(documentNumber == null || documentNumber.isBlank() ? null : documentNumber, country, null, null)) {
            case IdentityLookup.Found found -> new DocumentLookup("Encontrado: " + found.customerId(), found);
            case IdentityLookup.NotFound ignored -> new DocumentLookup("Ningún cliente tiene ese documento", null);
            case IdentityLookup.Ambiguous a -> new DocumentLookup(a.count() + " clientes tienen ese documento: ambiguo, "
                    + "no se muestran sus datos (pide el país emisor o la fecha de nacimiento)", null);
        };
    }

    @Tool(description = "Customers a guest may be: the same name (however spelled) and birth date, the same nationality first; at most 5. "
            + "Without a birth date, none. Only suggests — never merges")
    public List<IdentityLookup.Candidate> findCandidates(
            @ToolParam(description = "First name") String firstName,
            @ToolParam(description = "Last name(s)") String lastName,
            @ToolParam(description = "Birth date, ISO (1984-03-02)") String birthDate,
            @ToolParam(description = "Nationality, ISO-2; empty if unknown", required = false) String nationality) {
        if (birthDate == null || birthDate.isBlank()) {
            return List.of();
        }
        return lookup.candidates(firstName, lastName, java.time.LocalDate.parse(birthDate.trim()), nationality);
    }

    @Tool(description = "A customer by any code it ever had — an absorbed code answers with its survivor — with its aliases, reservations and what won each field in its last merge")
    public CustomerView getCustomer(@ToolParam(description = "The customer code, e.g. C-1A2B3C4D5E6F") String id) {
        return api.customer(id);
    }
}
