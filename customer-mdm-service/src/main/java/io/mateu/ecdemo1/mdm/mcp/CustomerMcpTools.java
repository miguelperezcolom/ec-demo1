package io.mateu.ecdemo1.mdm.mcp;

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

    public String getSystemContext() {
        return """
                Maestro de clientes (MDM) — la vista única del cliente:
                - Cada pasajero de una reserva se resuelve a un cliente (golden record) antes de llegar a Opera. Si
                  no hay coincidencia segura (documento, o email con el mismo nombre) se crea un cliente PROVISIONAL:
                  la venta nunca espera a la limpieza.
                - Los provisionales se envían a Salesforce, que detecta duplicados; un data steward los fusiona allí.
                  El MDM recibe la fusión y decide la supervivencia: el absorbido queda como alias (MERGED) del
                  superviviente (CONSOLIDATED), y sus reservas se proyectan de nuevo con el código superviviente.
                - Salesforce limpia; el maestro es el MDM. Estas herramientas solo consultan: fusionar se hace en
                  Salesforce, no desde el chat.
                - Son datos personales: muestra solo lo que el usuario necesite para su pregunta.
                """;
    }

    @Tool(description = "Customers (golden records) whose name, email, document or code contains the text; absorbed ones are left out")
    public List<CustomerView> findCustomers(@ToolParam(description = "Text to look for; empty for the most recently changed") String text) {
        return api.search(text == null ? "" : text);
    }

    @Tool(description = "A customer by any code it ever had — an absorbed code answers with its survivor — with its aliases, reservations and what won each field in its last merge")
    public CustomerView getCustomer(@ToolParam(description = "The customer code, e.g. C-1A2B3C4D5E6F") String id) {
        return api.customer(id);
    }
}
