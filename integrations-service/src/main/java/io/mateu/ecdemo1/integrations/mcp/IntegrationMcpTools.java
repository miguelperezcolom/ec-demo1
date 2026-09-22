package io.mateu.ecdemo1.integrations.mcp;

import io.mateu.ecdemo1.integrations.lifecycle.Integrations;
import io.mateu.ecdemo1.integrations.rest.IntegrationController;
import io.mateu.ecdemo1.integrations.rest.IntegrationDto;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * The integrations as tools for an agent: where each hotel's onboarding is and what it waits for,
 * and the actions a person takes on it. Registering one is not a tool: it needs the Opera secret,
 * and a secret has no business in a chat.
 */
@Component
@RequiredArgsConstructor
public class IntegrationMcpTools implements McpSystemContext {

    final IntegrationController api;
    final Integrations lifecycle;
    final IntegrationRepository integrations;

    @Override
    public String getSystemContext() {
        return """
                Integraciones CRS → PMS (Opera), una por hotel:
                - Cada hotel del CRS tiene una integración con su propiedad de Opera y sus credenciales. Nada de
                  ese hotel llega a Opera en tiempo real hasta que su integración está ACTIVE: mientras tanto sus
                  reservas esperan en la causa INTEGRATION_INACTIVE/<hotel> y se reanudan al activarla.
                - El alta avanza por puertas: conectividad, propiedad configurada en Opera, mapeado aprobado por
                  una persona, interlocutores proyectados, huecos del backfill resueltos, backfill cubriendo la
                  ventana de activación, y activación por una persona. getIntegration dice en qué puerta está
                  («waitingFor») y qué falta.
                - Aprobar el mapeado, activar, pausar, reanudar y relanzar el backfill son decisiones de una
                  persona: hazlo solo si el usuario lo pide expresamente, indicando su nombre.
                - El alta de una integración no se hace desde el chat: necesita el secreto de Opera. Se hace en la
                  consola, en Integrations.
                """;
    }

    @Tool(description = "Every hotel's integration: its Opera property, its status and what its onboarding waits for")
    public List<IntegrationDto> listIntegrations() {
        return api.list();
    }

    @Tool(description = "One hotel's integration in full: connection check, catalogue contrast, pending mappings, "
            + "partners missing, backfill gaps and progress, history")
    public IntegrationDto getIntegration(@ToolParam(description = "CRS hotel code, e.g. PMI01") String hotelCode) {
        return api.get(id(hotelCode));
    }

    @Tool(description = "Look again at what the onboarding's current gate needs: the property's catalogue, the partners, the backfill's gaps")
    public IntegrationDto recheck(@ToolParam(description = "CRS hotel code") String hotelCode) {
        return api.recheck(id(hotelCode), "agent");
    }

    @Tool(description = "Approve the hotel's mapping so its onboarding goes on. Only when the user asks for it, with their name")
    public IntegrationDto approveMapping(@ToolParam(description = "CRS hotel code") String hotelCode,
                                         @ToolParam(description = "The name of the person approving") String approvedBy) {
        return api.approveMapping(id(hotelCode), named(approvedBy));
    }

    @Tool(description = "Activate the hotel's integration: real-time traffic flows. Only when the user asks for it, with their name")
    public IntegrationDto activate(@ToolParam(description = "CRS hotel code") String hotelCode,
                                   @ToolParam(description = "The name of the person activating") String activatedBy) {
        return api.activate(id(hotelCode), named(activatedBy));
    }

    @Tool(description = "Pause an active integration: the hotel's reservations wait until it is resumed")
    public IntegrationDto pause(@ToolParam(description = "CRS hotel code") String hotelCode,
                                @ToolParam(description = "The name of the person pausing") String pausedBy) {
        return api.pause(id(hotelCode), named(pausedBy));
    }

    @Tool(description = "Resume a paused integration: what waited flows, and real-time traffic with it")
    public IntegrationDto resume(@ToolParam(description = "CRS hotel code") String hotelCode,
                                 @ToolParam(description = "The name of the person resuming") String resumedBy) {
        return api.resume(id(hotelCode), named(resumedBy));
    }

    @Tool(description = "Relaunch the backfill of a running integration: its future reservations are projected again, nearest first")
    public IntegrationDto relaunchBackfill(@ToolParam(description = "CRS hotel code") String hotelCode,
                                           @ToolParam(description = "The name of the person relaunching") String by) {
        return api.relaunchBackfill(id(hotelCode), named(by));
    }

    String id(String hotelCode) {
        return integrations.findByCrsHotelCode(hotelCode).map(i -> i.id)
                .orElseThrow(() -> new NoSuchElementException("Hotel %s has no integration".formatted(hotelCode)));
    }

    static String named(String name) {
        if (name == null || name.isBlank() || "agent".equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("A person's name is needed: this is their decision, not the agent's");
        }
        return name;
    }
}
