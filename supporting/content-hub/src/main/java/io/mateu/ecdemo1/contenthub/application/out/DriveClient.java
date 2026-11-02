package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;

/**
 * Google Drive, for what the intranet knows of the files it references: their metadata (Drive API v3
 * {@code files.get}). Never their content — the intranet keeps a reference and shows Google's own preview.
 * Optional: without {@code GOOGLE_DRIVE_*} configuration it is not {@link #configured()} and nothing asks it.
 */
public interface DriveClient {

    boolean configured();

    /**
     * @throws DriveException when Drive cannot be asked or refuses (no access, not found…), with a reason
     *                        an editor can read
     */
    DriveFile.Metadata metadata(String fileId);

    class DriveException extends RuntimeException {
        public DriveException(String message) {
            super(message);
        }

        public DriveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
