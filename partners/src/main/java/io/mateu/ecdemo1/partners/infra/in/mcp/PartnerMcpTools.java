package io.mateu.ecdemo1.partners.infra.in.mcp;

import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.application.usecases.PartnerService;
import io.mateu.ecdemo1.partners.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.partners.infra.in.rest.PartnerDto;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerMcpTools implements McpSystemContext {

    final PartnerRepository repository;
    final PartnerService service;

    @Override
    public String getSystemContext() {
        return """
                Maestro de interlocutores (partners):
                - Un interlocutor es una agencia (TravelAgent), un turoperador (TourOperator), una agencia
                  online (OnlineAgency) o una empresa pagadora (Company), con sus datos fiscales y de
                  facturación.
                - billingMode dice quién paga la estancia: Front (el huésped en recepción) o NoFront (el
                  interlocutor, a crédito).
                - El código de un interlocutor no cambia nunca: es cómo lo referencian las reservas y el PMS.
                - resyncPartner vuelve a anunciar un interlocutor sin cambiarlo, para que la integración lo
                  proyecte otra vez al PMS.
                """;
    }

    @Tool(description = "List partners; the search text matches the code or the name, empty lists all")
    public List<PartnerDto> listPartners(@ToolParam(required = false) String search) {
        return repository.search(search, 0, 100).stream().map(PartnerDto::of).toList();
    }

    @Tool(description = "Read a partner by its code")
    public Object getPartner(String code) {
        return repository.findByCode(code).<Object>map(PartnerDto::of).orElse("Partner not found: " + code);
    }

    @Tool(description = "Create a partner. The code is 2 to 30 upper-case letters, digits or dashes and never changes")
    public String createPartner(String code, PartnerDetails details) {
        log.info("MCP createPartner {}", code);
        return attempt(() -> "Partner %s created".formatted(service.create(code, details)));
    }

    @Tool(description = "Replace a partner's details as a whole")
    public String updatePartner(String code, PartnerDetails details) {
        log.info("MCP updatePartner {}", code);
        return attempt(() -> {
            service.update(code, details);
            return "Partner %s updated".formatted(code);
        });
    }

    @Tool(description = "Announce a partner again, unchanged, so the integration projects it to the PMS once more")
    public String resyncPartner(String code) {
        log.info("MCP resyncPartner {}", code);
        return attempt(() -> {
            service.resync(code);
            return "Partner %s announced again".formatted(code);
        });
    }

    private static String attempt(Supplier<String> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            return "Error: " + e.getMessage();
        }
    }
}
