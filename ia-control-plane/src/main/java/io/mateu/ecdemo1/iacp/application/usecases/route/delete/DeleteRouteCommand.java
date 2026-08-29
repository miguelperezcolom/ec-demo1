package io.mateu.ecdemo1.iacp.application.usecases.route.delete;

import java.util.List;

public record DeleteRouteCommand(List<String> ids) {
}
