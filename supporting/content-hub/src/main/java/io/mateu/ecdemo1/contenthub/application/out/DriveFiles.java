package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** The metadata last read from Drive for each referenced file. */
public interface DriveFiles {

    void save(DriveFile file);

    Optional<DriveFile> find(String fileId);

    Map<String, DriveFile> findAll(Collection<String> fileIds);
}
