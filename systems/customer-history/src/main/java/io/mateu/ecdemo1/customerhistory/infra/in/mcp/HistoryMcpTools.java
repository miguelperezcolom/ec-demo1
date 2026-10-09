package io.mateu.ecdemo1.customerhistory.infra.in.mcp;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * A customer's stay history, for the data plane's agents: read-only. The tool answers the summary
 * record itself — never Object, which Spring AI silently leaves out of the tools it offers.
 */
@Component
@Slf4j
public class HistoryMcpTools implements McpSystemContext {

    final CustomerHistory history;

    public HistoryMcpTools(CustomerHistory history) {
        this.history = history;
    }

    @Override
    public String getSystemContext() {
        return """
                Historial de clientes:
                - Las estancias de cada cliente en la cadena, por su código del MDM (C-…): cuántas, cuántas
                  noches, la primera y la última, las tres más recientes (hotel, fechas, habitación, tipo),
                  en cuántos hoteles, su hotel más repetido y lo que gastó en recepción (extras, late
                  check-out y consumos; el alojamiento no cuenta).
                - Un código que el MDM fusionó con otro responde con el historial del superviviente: el
                  customerId de la respuesta es el código vigente.
                - Un cliente sin estancias tiene un historial a cero, no un error.
                """;
    }

    @Tool(description = "A customer's stay history in the chain, by their MDM customer code (C-...): stays, nights, "
            + "first and last stay, the 3 most recent stays, hotels, the hotel they stayed at most and what they spent "
            + "at the desk. A merged code answers as its survivor (customerId). Zeros if they have no stays")
    public CustomerHistory.Summary getCustomerHistory(@ToolParam(description = "The MDM's customer code, e.g. C-00042")
                                                      String code) {
        log.info("MCP getCustomerHistory {}", code);
        return history.summary(code);
    }
}
