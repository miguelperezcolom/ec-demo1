package io.mateu.ecdemo1.apimcp.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Optional;

/**
 * One route bean for a set of endpoints that does not exist yet when it is created.
 *
 * <p>Spring collects {@code RouterFunction} beans once, at startup, and the endpoints this service
 * serves come and go with the catalogue. The way out is that {@code RouterFunction} is a
 * functional interface: a bean that decides which handler answers a request AT REQUEST TIME can
 * consult a registry that has changed since it was built. Each mounted entry keeps its own
 * transport provider with its own paths, and this asks each of them in turn whether the request is
 * theirs.
 *
 * <p>The cost is a linear walk per request over the mounted entries, which is the right shape for
 * a catalogue an operator maintains by hand and the wrong one for thousands. Worth saying out loud
 * so that whoever hits that limit knows this is where to look.
 */
@Configuration
@Slf4j
public class ApiMcpRouting {

    @Bean
    public RouterFunction<ServerResponse> apiMcpEndpoints(ApiMcpServers servers) {
        return request -> servers.routerFunctions().stream()
                .map(routerFunction -> routerFunction.route(request))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
