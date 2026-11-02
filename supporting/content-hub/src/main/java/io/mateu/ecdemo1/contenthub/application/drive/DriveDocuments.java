package io.mateu.ecdemo1.contenthub.application.drive;

import io.mateu.ecdemo1.contenthub.application.out.DriveClient;
import io.mateu.ecdemo1.contenthub.application.out.DriveFiles;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFileRef;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Google Drive files the intranet references — a Drive URL as an item's own (a document, a link) or a
 * Drive medium of the library — never copies: the item shows Google's live preview and opens the file in
 * Drive, so there is one version, Drive's.
 *
 * <p>With Drive configured ({@link DriveClient#configured()}), {@link #refresh()} reads each referenced
 * file's metadata so pages say «Modificado por X el …» with the file's current name. Without it, all the
 * rest works the same, from the URL alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriveDocuments {

    final Visibility visibility;
    final DriveFiles files;
    final DriveClient client;
    final MediaRepository media;
    final ItemRepository items;
    final Clock clock;

    /** A Drive file as the reader finds it: the item it is in, and what Drive last said of it (may be null). */
    public record Document(DriveFileRef ref, String itemId, String itemTitle, DriveFile metadata) {

        /** The file's name in Drive when known, else the item's title. */
        public String name() {
            return metadata != null && metadata.name() != null ? metadata.name() : itemTitle;
        }
    }

    /**
     * The Drive files of the items the reader sees, once each (with the newest item that uses it) — the
     * items come from the visibility function, as everything that enumerates content.
     */
    public List<Document> visibleTo(EmployeeAttributes reader) {
        var byFile = new LinkedHashMap<String, Document>();
        for (var r : visibility.googleUrls(reader)) {
            DriveFileRef.parse(r.url()).ifPresent(ref -> byFile.putIfAbsent(ref.fileId(),
                    new Document(ref, r.itemId(), r.itemTitle(), null)));
        }
        var known = files.findAll(byFile.keySet());
        var out = new ArrayList<Document>();
        byFile.values().forEach(d -> {
            var m = known.get(d.ref().fileId());
            out.add(new Document(m == null ? d.ref() : d.ref().withMimeType(m.mimeType()), d.itemId(), d.itemTitle(), m));
        });
        return out;
    }

    /**
     * The Drive files an item the reader already sees uses: the one its URL points at (directly or as a
     * medium), then those embedded in its text. The caller got the item from the visibility function.
     */
    public List<Document> ofItem(VisibleItem item) {
        var refs = new LinkedHashMap<String, DriveFileRef>();
        var url = item.url();
        if (url != null && url.startsWith("media:")) {
            ofMedium(url.substring(6)).ifPresent(ref -> refs.putIfAbsent(ref.fileId(), ref));
        } else {
            DriveFileRef.parse(url).ifPresent(ref -> refs.putIfAbsent(ref.fileId(), ref));
        }
        if (item.body() != null) {
            var marks = MediaReferences.MARK.matcher(item.body());
            while (marks.find()) {
                ofMedium(marks.group(1)).ifPresent(ref -> refs.putIfAbsent(ref.fileId(), ref));
            }
        }
        if (refs.isEmpty()) {
            return List.of();
        }
        var known = files.findAll(refs.keySet());
        return refs.values().stream().map(ref -> {
            var m = known.get(ref.fileId());
            return new Document(m == null ? ref : ref.withMimeType(m.mimeType()), item.itemId(), item.title(), m);
        }).toList();
    }

    Optional<DriveFileRef> ofMedium(String mediaId) {
        return media.find(mediaId).filter(a -> a.kind() == MediaKind.EXTERNAL)
                .flatMap(a -> DriveFileRef.parse(a.externalUrl()));
    }

    /** What Drive last said of a file, if it was ever asked. */
    public Optional<DriveFile> metadata(String fileId) {
        return files.find(fileId);
    }

    public boolean configured() {
        return client.configured();
    }

    /** Every Drive file referenced anywhere: the library's Drive media and the items' URLs (draft or published). */
    public Set<String> referencedFileIds() {
        var out = new LinkedHashSet<String>();
        for (var a : media.search("", 0, 100_000)) {
            if (a.kind() == MediaKind.EXTERNAL) {
                DriveFileRef.parse(a.externalUrl()).ifPresent(ref -> out.add(ref.fileId()));
            }
        }
        for (var item : items.findAll()) {
            DriveFileRef.parse(item.draft().url()).ifPresent(ref -> out.add(ref.fileId()));
            item.publishedVersion().flatMap(v -> DriveFileRef.parse(v.content().url())).ifPresent(ref -> out.add(ref.fileId()));
        }
        return out;
    }

    /** @return how many files were refreshed; 0 without Drive configured */
    public int refresh() {
        if (!client.configured()) {
            return 0;
        }
        var n = 0;
        for (var id : referencedFileIds()) {
            if (refresh(id)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Reads one file's metadata from Drive. A failure (no access, deleted…) is kept with the file, next to
     * what was known before, so the page still shows the last known name.
     *
     * @return whether Drive answered
     */
    public boolean refresh(String fileId) {
        if (!client.configured()) {
            return false;
        }
        var now = clock.instant();
        try {
            files.save(DriveFile.refreshed(fileId, client.metadata(fileId), now));
            return true;
        } catch (RuntimeException e) {
            log.info("Drive metadata of {} not refreshed: {}", fileId, e.getMessage());
            files.save(files.find(fileId).map(f -> f.failed(e.getMessage(), now))
                    .orElseGet(() -> DriveFile.unknown(fileId, e.getMessage(), now)));
            return false;
        }
    }
}
