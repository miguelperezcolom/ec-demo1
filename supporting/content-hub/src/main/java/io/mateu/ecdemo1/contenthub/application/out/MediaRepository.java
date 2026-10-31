package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {

    /** @param bytes the file, for an uploaded one; null for an external URL */
    void save(MediaAsset asset, byte[] bytes);

    Optional<MediaAsset> find(String id);

    Optional<byte[]> bytes(String id);

    List<MediaAsset> search(String text, int offset, int limit);

    long count(String text);

    void delete(String id);
}
