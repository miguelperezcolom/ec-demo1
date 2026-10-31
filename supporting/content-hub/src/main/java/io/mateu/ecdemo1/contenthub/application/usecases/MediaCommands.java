package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The media library: small files are uploaded and kept here; bigger ones live where they belong
 * (YouTube, Drive…) and only their URL is kept.
 */
@Service
@RequiredArgsConstructor
public class MediaCommands {

    final MediaRepository media;
    final Clock clock;

    @Value("${content-hub.media.max-bytes:20971520}")
    long maxBytes;

    @Transactional
    public String upload(Reader reader, String title, String fileName, String contentType, byte[] bytes) {
        reader.require(EditorialPermission.EDIT);
        var id = newId();
        var asset = MediaAsset.uploaded(id, title, fileName, contentType, bytes.length, maxBytes, reader.userId(),
                clock.instant());
        media.save(asset, bytes);
        return id;
    }

    @Transactional
    public String external(Reader reader, String title, String url) {
        reader.require(EditorialPermission.EDIT);
        var id = newId();
        media.save(MediaAsset.external(id, title, url, reader.userId(), clock.instant()), null);
        return id;
    }

    @Transactional
    public void delete(Reader reader, String id) {
        reader.require(EditorialPermission.EDIT);
        media.delete(id);
    }

    public long maxBytes() {
        return maxBytes;
    }

    static String newId() {
        return "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
