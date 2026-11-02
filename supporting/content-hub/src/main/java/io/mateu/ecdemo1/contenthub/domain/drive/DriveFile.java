package io.mateu.ecdemo1.contenthub.domain.drive;

import java.time.Instant;

/**
 * What Drive last said about a referenced file (Drive API v3 {@code files.get}): its current name and type,
 * when and by whom it was last modified, and its links. A cache refreshed by a job when Drive is configured —
 * never the file itself — so the intranet shows «Modificado por X el …» and the up-to-date title.
 *
 * @param error why the last refresh failed (no access, deleted…); null when it went well
 */
public record DriveFile(String fileId, String name, String mimeType, Instant modifiedTime, String lastModifiedBy,
                        String webViewLink, String thumbnailLink, Instant refreshedAt, String error) {

    /** The metadata Drive answered, as the client maps it. */
    public record Metadata(String name, String mimeType, Instant modifiedTime, String lastModifiedBy,
                           String webViewLink, String thumbnailLink) {
    }

    public static DriveFile refreshed(String fileId, Metadata m, Instant at) {
        return new DriveFile(fileId, m.name(), m.mimeType(), m.modifiedTime(), m.lastModifiedBy(), m.webViewLink(),
                m.thumbnailLink(), at, null);
    }

    /** A failed refresh keeps what was known and says why. */
    public DriveFile failed(String why, Instant at) {
        return new DriveFile(fileId, name, mimeType, modifiedTime, lastModifiedBy, webViewLink, thumbnailLink, at, why);
    }

    public static DriveFile unknown(String fileId, String why, Instant at) {
        return new DriveFile(fileId, null, null, null, null, null, null, at, why);
    }
}
