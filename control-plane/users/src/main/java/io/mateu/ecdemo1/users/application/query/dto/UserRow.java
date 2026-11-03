package io.mateu.ecdemo1.users.application.query.dto;

public record UserRow(@io.mateu.uidl.annotations.Label("Usuario") String id, @io.mateu.uidl.annotations.Label("Nombre") String name,
                      @io.mateu.uidl.annotations.Label("Email") String email) {
}
