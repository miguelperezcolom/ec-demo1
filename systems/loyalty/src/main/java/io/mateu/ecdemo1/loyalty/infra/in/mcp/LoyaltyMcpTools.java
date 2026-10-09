package io.mateu.ecdemo1.loyalty.infra.in.mcp;

import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.infra.in.rest.AccrualView;
import io.mateu.ecdemo1.loyalty.infra.in.rest.MemberView;
import io.mateu.ecdemo1.loyalty.store.MemberRepository;
import io.mateu.ecdemo1.loyalty.store.Tier;
import io.mateu.workflow.mcp.McpSystemContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Riu Class, for the data plane's agent: read-only — a member, a customer's membership, the members of a
 * tier and what each stay earned. Every tool answers a view or a list of views — never Object, which
 * Spring AI does not offer as a tool at all.
 */
@Component
@Slf4j
public class LoyaltyMcpTools implements McpSystemContext {

    final Loyalty loyalty;
    final MemberRepository members;

    public LoyaltyMcpTools(Loyalty loyalty, MemberRepository members) {
        this.loyalty = loyalty;
        this.members = members;
    }

    @Override
    public String getSystemContext() {
        return """
                Riu Class (programa de fidelización de la cadena):
                - Un socio tiene un número de tarjeta (RC12345678), el código de cliente del MDM (C-…),
                  un nivel (SILVER, GOLD o PLATINUM) y sus puntos.
                - Nivel por puntos: SILVER por debajo de 10.000, GOLD desde 10.000, PLATINUM desde 40.000.
                  Un nivel concedido a mano se respeta; los puntos solo lo suben.
                - Cada estancia cerrada en el front office acumula 100 puntos por noche al titular y 50 por
                  noche a cada acompañante que sea socio, una sola vez por estancia.
                - Si el MDM fusiona dos clientes, la tarjeta del absorbido pasa al superviviente.
                """;
    }

    @Tool(description = "A Riu Class member by its card number (e.g. RC12345678): tier, points, customer code; "
            + "null if there is no such member")
    public MemberView getMember(String memberNumber) {
        log.info("MCP getMember {}", memberNumber);
        return loyalty.find(memberNumber).map(MemberView::of).orElse(null);
    }

    @Tool(description = "The Riu Class membership of a customer, by its MDM customer code (C-…); null if the "
            + "customer is not a member")
    public MemberView findMemberByCustomer(String customerCode) {
        log.info("MCP findMemberByCustomer {}", customerCode);
        return loyalty.findByCustomer(customerCode).map(MemberView::of).orElse(null);
    }

    @Tool(description = "Riu Class members, most recently changed first (at most 100). tier: SILVER, GOLD or "
            + "PLATINUM (empty, any)")
    public List<MemberView> listMembers(@ToolParam(required = false) String tier) {
        var page = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "updatedAt"));
        var wanted = Tier.parse(tier);  // an unknown tier is an error the agent sees, not an empty list
        var found = wanted == null ? members.findAll(page).getContent() : members.findByTier(wanted.name(), page);
        return found.stream().map(MemberView::of).toList();
    }

    @Tool(description = "The points each stay earned a Riu Class member, newest first, by its card number")
    public List<AccrualView> getAccruals(String memberNumber) {
        return loyalty.accrualsOf(memberNumber).stream().map(AccrualView::of).toList();
    }
}
