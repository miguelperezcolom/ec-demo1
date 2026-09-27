package io.mateu.ecdemo1.communication.store;

/**
 * How a recipient is told: in the inbox of its people, in their browsers (Web Push) — the consoles', or
 * the front office's at the desk — by e-mail, or in the Google Chat spaces it names.
 *
 * <p>The desk's browsers are a channel of their own so that the recipients decide what reaches the
 * front desk apart from what reaches the consoles: the same person signed in to both would otherwise
 * be told everything twice, once per origin.
 */
public enum Channel {
    INBOX, WEB_PUSH, EMAIL, GOOGLE_CHAT, FRONT_DESK_PUSH
}
