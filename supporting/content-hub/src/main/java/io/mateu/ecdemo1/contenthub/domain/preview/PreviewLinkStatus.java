package io.mateu.ecdemo1.contenthub.domain.preview;

/** Whether a preview link still shows its draft, and if not, why. */
public enum PreviewLinkStatus {
    ACTIVE("Activo"),
    EXPIRED("Caducado"),
    REVOKED("Revocado"),
    /** The item was published after the link was made: it is in the intranet now, for its readers. */
    PUBLISHED("Sin efecto: ya se ha publicado");

    private final String label;

    PreviewLinkStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
