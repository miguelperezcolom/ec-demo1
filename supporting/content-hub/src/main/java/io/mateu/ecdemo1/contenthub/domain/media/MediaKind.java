package io.mateu.ecdemo1.contenthub.domain.media;

import java.util.Locale;

public enum MediaKind {
    IMAGE("Imagen"), PDF("PDF"), AUDIO("Audio"), VIDEO("Vídeo"),
    /** Not stored here: a YouTube/Vimeo video, a Drive file, any URL. */
    EXTERNAL("Enlace externo");

    private final String label;

    MediaKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** What an uploaded file is, by its content type; null when it is none of the kinds accepted. */
    public static MediaKind ofContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        var ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/") && !ct.contains("svg")) {
            return IMAGE;
        }
        if (ct.equals("application/pdf")) {
            return PDF;
        }
        if (ct.startsWith("audio/")) {
            return AUDIO;
        }
        if (ct.startsWith("video/")) {
            return VIDEO;
        }
        return null;
    }
}
