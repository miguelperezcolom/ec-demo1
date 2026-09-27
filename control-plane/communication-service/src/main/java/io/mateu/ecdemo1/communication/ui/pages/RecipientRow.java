package io.mateu.ecdemo1.communication.ui.pages;

/** A recipient in the list: who, about what, where. */
public record RecipientRow(String id, String name, String who, String what, String hotel, String where, boolean active) {
}
