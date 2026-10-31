package io.mateu.ecdemo1.contenthub.domain.media;

import java.time.Instant;

/**
 * A file of the media library — uploaded, when small enough — or an external URL for the rest. It is
 * embedded in content with a MEDIA block or a {@code [[media:id]]} mark in a body, and served only to
 * someone who can see a published item that uses it.
 *
 * @param size bytes of an uploaded file; 0 for an external one
 */
public record MediaAsset(String id, MediaKind kind, String title, String fileName, String contentType, long size,
                         String externalUrl, String uploadedBy, Instant uploadedAt) {

    public MediaAsset {
        if (kind == null) {
            throw new IllegalArgumentException("Tipo de medio no admitido");
        }
        if (kind == MediaKind.EXTERNAL) {
            ExternalMedia.of(externalUrl);
        }
    }

    public static MediaAsset uploaded(String id, String title, String fileName, String contentType, long size,
                                      long maxBytes, String by, Instant at) {
        var kind = MediaKind.ofContentType(contentType);
        if (kind == null) {
            throw new IllegalArgumentException("Tipo de fichero no admitido: " + contentType
                    + " (imágenes, PDF, audio y vídeo)");
        }
        if (size > maxBytes) {
            throw new IllegalArgumentException("El fichero ocupa " + (size / 1024 / 1024) + " MB; el máximo es "
                    + (maxBytes / 1024 / 1024) + " MB. Para ficheros mayores, súbelo a YouTube, Drive… e indica su URL");
        }
        return new MediaAsset(id, kind, blank(title) ? fileName : title, fileName, contentType, size, null, by, at);
    }

    public static MediaAsset external(String id, String title, String url, String by, Instant at) {
        var ext = ExternalMedia.of(url);
        return new MediaAsset(id, MediaKind.EXTERNAL, blank(title) ? ext.provider().name() : title, null, null, 0,
                ext.url(), by, at);
    }

    public ExternalMedia external() {
        return kind == MediaKind.EXTERNAL ? ExternalMedia.of(externalUrl) : null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
