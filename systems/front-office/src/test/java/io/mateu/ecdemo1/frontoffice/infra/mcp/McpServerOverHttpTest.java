package io.mateu.ecdemo1.frontoffice.infra.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.frontoffice.application.CheckInService;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.room.HousekeepingStatus;
import io.mateu.ecdemo1.frontoffice.domain.room.Room;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomOccupancy;
import io.mateu.ecdemo1.frontoffice.domain.room.RoomRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The MCP server as ia-agent reaches it: over SSE, with the person's token, one session per prompt. It
 * refuses a call with no token, and a confirmation is only accepted on a later session than the one
 * that prepared it — the person's own next message — and audited for the person the token names.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:mcp-http;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
@Import(McpServerOverHttpTest.Tokens.class)
class McpServerOverHttpTest {

  /** "Bearer ana-token" is Ana's token; anything else is no token at all. */
  /** Imported, not scanned: the other tests' contexts keep the application's decoder. */
  static class Tokens {
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        if (!"ana-token".equals(token)) {
          throw new BadJwtException("unknown token");
        }
        return Jwt.withTokenValue(token).header("alg", "none").subject("u-ana")
            .claim("preferred_username", "ana").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .build();
      };
    }
  }

  @LocalServerPort int port;
  @Autowired StayRepository stays;
  @Autowired GuestRepository guests;
  @Autowired RoomRepository rooms;
  @Autowired FolioRepository folios;
  @Autowired CheckInService checkIn;
  @Autowired JdbcTemplate jdbc;

  McpSyncClient client(String bearer) {
    var transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
        .httpRequestCustomizer((request, method, uri, body, context) -> {
          if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
          }
        })
        .build();
    var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(20)).build();
    client.initialize();
    return client;
  }

  static String text(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }

  @Test
  void theAgentsCallsNeedThePersonsToken() throws Exception {
    var http = HttpClient.newHttpClient();
    for (var bearer : new String[] {null, "forged"}) {
      var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sse")).GET();
      if (bearer != null) {
        request.header("Authorization", "Bearer " + bearer);
      }
      assertThat(http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
    }
    var message = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp/message?sessionId=x"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
    assertThat(http.send(message, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
  }

  @Test
  void aConfirmationIsOnlyTakenInTheNextPromptAndIsAuditedForThePerson() {
    guests.save(Guest.fromReservation("C-HTTP1", "Ana Http", "X1", null, null).verifyIdentity("X1"));
    rooms.save(new Room("7701", 77, "Doble", RoomOccupancy.FREE, HousekeepingStatus.CLEAN, null));
    stays.save(Stay.fromReservation("HTTP-1", "C-HTTP1", "Doble", "SA", LocalDate.now().minusDays(1),
        LocalDate.now().plusDays(1), 1, null, new BigDecimal("100.00"), List.of()));
    checkIn.registrationSigned("HTTP-1");
    checkIn.checkIn("HTTP-1", "7701", List.of());

    String token;
    try (var firstPrompt = client("ana-token")) {
      assertThat(firstPrompt.listTools().tools()).extracting(McpSchema.Tool::name)
          .contains("listArrivals", "getStay", "prepareLateCheckOut", "confirmAction");
      var prepared = text(firstPrompt.callTool(new McpSchema.CallToolRequest("prepareLateCheckOut",
          Map.of("stayRef", "HTTP-1"))));
      token = FrontDeskMcpToolsTest.token(prepared);

      var sameTurn = text(firstPrompt.callTool(new McpSchema.CallToolRequest("confirmAction", Map.of("token", token))));
      assertThat(sameTurn).contains("la persona todavía no ha confirmado");
      assertThat(folios.findByStayId("HTTP-1").orElseThrow().lateCheckOutContracted()).isFalse();
    }

    try (var nextPrompt = client("ana-token")) {
      var confirmed = text(nextPrompt.callTool(new McpSchema.CallToolRequest("confirmAction", Map.of("token", token))));
      assertThat(confirmed).contains("Hecho. Late check-out de HTTP-1 contratado");
    }
    assertThat(folios.findByStayId("HTTP-1").orElseThrow().lateCheckOutContracted()).isTrue();
    assertThat(jdbc.queryForList("select payload from outbox_message where binding = 'audit' order by seq", String.class))
        .anySatisfy(p -> assertThat(p).contains("HTTP-1").contains("\"by\":\"reception-agent (ana)\""));
  }
}
