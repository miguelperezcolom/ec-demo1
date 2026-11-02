package io.mateu.ecdemo1.contenthub.application.drive;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.DriveClient;
import io.mateu.ecdemo1.contenthub.application.out.DriveFiles;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFileRef;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The references and the metadata job, with a fake Drive. */
class DriveDocumentsTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final String SEEN = "seenFile_12345";
    static final String HIDDEN = "hiddenFile_1234";
    static final DriveFile.Metadata PLAN = new DriveFile.Metadata("Plan 2027", "application/vnd.google-apps.document",
            Instant.parse("2026-10-30T09:15:00Z"), "Ana Ruiz", "https://docs.google.com/x", "https://lh3/t");

    /** What a test's Drive knows; anything else, it cannot see. */
    static final class FakeDrive implements DriveClient {
        boolean configured = true;
        final Map<String, DriveFile.Metadata> files = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public DriveFile.Metadata metadata(String fileId) {
            asked.add(fileId);
            var m = files.get(fileId);
            if (m == null) {
                throw new DriveException("Drive no encuentra el fichero");
            }
            return m;
        }
    }

    static final class MemoryFiles implements DriveFiles {
        final Map<String, DriveFile> saved = new LinkedHashMap<>();

        @Override
        public void save(DriveFile file) {
            saved.put(file.fileId(), file);
        }

        @Override
        public Optional<DriveFile> find(String fileId) {
            return Optional.ofNullable(saved.get(fileId));
        }

        @Override
        public Map<String, DriveFile> findAll(Collection<String> fileIds) {
            var out = new LinkedHashMap<String, DriveFile>();
            fileIds.forEach(id -> find(id).ifPresent(f -> out.put(id, f)));
            return out;
        }
    }

    final FakeDrive drive = new FakeDrive();
    final MemoryFiles files = new MemoryFiles();
    final List<MediaAsset> library = new ArrayList<>();
    final List<ContentIndex.ExternalReference> visible = new ArrayList<>();

    @SuppressWarnings("unchecked")
    <T> T stub(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, handler);
    }

    DriveDocuments documents() {
        var index = stub(ContentIndex.class, (p, m, a) -> switch (m.getName()) {
            case "knowsProfile" -> true;
            case "visibleGoogleUrls" -> visible;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var media = stub(MediaRepository.class, (p, m, a) -> switch (m.getName()) {
            case "search" -> library;
            case "find" -> library.stream().filter(x -> x.id().equals(a[0])).findFirst();
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var items = stub(ItemRepository.class, (p, m, a) -> switch (m.getName()) {
            case "findAll" -> List.of();
            default -> throw new UnsupportedOperationException(m.getName());
        });
        return new DriveDocuments(new Visibility(index, null, Clock.fixed(NOW, ZoneOffset.UTC)), files, drive, media, items,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static String docUrl(String id) {
        return "https://docs.google.com/document/d/" + id + "/edit";
    }

    MediaAsset medium(String id, String fileId) {
        var asset = MediaAsset.external(id, "Medio " + id, docUrl(fileId), "eva", NOW);
        library.add(asset);
        return asset;
    }

    static VisibleItem item(String url, String body) {
        return new VisibleItem("i1", 1, "DOCUMENT", null, "es", "El item", "", body, url, "eva", NOW, null, false, false, null, 0);
    }

    @Test
    void the_job_refreshes_each_referenced_file() {
        medium("m1", SEEN);
        drive.files.put(SEEN, PLAN);

        assertThat(documents().refresh()).isEqualTo(1);

        var saved = files.saved.get(SEEN);
        assertThat(saved.name()).isEqualTo("Plan 2027");
        assertThat(saved.lastModifiedBy()).isEqualTo("Ana Ruiz");
        assertThat(saved.refreshedAt()).isEqualTo(NOW);
        assertThat(saved.error()).isNull();
    }

    @Test
    void without_drive_configured_nothing_is_asked() {
        drive.configured = false;
        medium("m1", SEEN);
        assertThat(documents().refresh()).isZero();
        assertThat(documents().refresh(SEEN)).isFalse();
        assertThat(drive.asked).isEmpty();
        assertThat(files.saved).isEmpty();
    }

    @Test
    void one_file_it_cannot_see_does_not_stop_the_others() {
        medium("m1", HIDDEN);
        medium("m2", SEEN);
        drive.files.put(SEEN, PLAN);
        assertThat(documents().refresh()).isEqualTo(1);
        assertThat(files.saved.get(HIDDEN).error()).contains("no encuentra");
        assertThat(files.saved.get(SEEN).error()).isNull();
    }

    @Test
    void a_failed_refresh_keeps_what_was_known() {
        medium("m1", SEEN);
        drive.files.put(SEEN, PLAN);
        documents().refresh();
        drive.files.clear();
        documents().refresh();
        assertThat(files.saved.get(SEEN).name()).isEqualTo("Plan 2027");
        assertThat(files.saved.get(SEEN).error()).isNotNull();
    }

    @Test
    void a_file_the_account_cannot_see_keeps_its_reference_preview_and_links_without_the_modified_line() {
        documents().refresh(HIDDEN); // Drive said no: kept with the reason, nothing else known

        var d = documents().ofItem(item(docUrl(HIDDEN), "")).getFirst();

        assertThat(d.name()).isEqualTo("El item");
        assertThat(d.ref().previewUrl()).contains(HIDDEN);
        assertThat(d.ref().openUrl()).contains(HIDDEN);
        assertThat(d.ref().editUrl()).hasValue(docUrl(HIDDEN));
        assertThat(PortalComponents.driveModified(d)).isEmpty();
    }

    @Test
    void with_metadata_the_item_says_who_modified_it_and_the_current_name() {
        drive.files.put(SEEN, PLAN);
        documents().refresh(SEEN);

        var d = documents().ofItem(item("https://drive.google.com/file/d/" + SEEN + "/view", "")).getFirst();

        assertThat(d.name()).isEqualTo("Plan 2027");
        assertThat(d.ref().type()).as("a share link is a Doc, as Drive says").isEqualTo(DriveFileRef.Type.DOCUMENT);
        assertThat(PortalComponents.driveModified(d)).isEqualTo("Modificado por Ana Ruiz el 30/10/2026 10:15");
    }

    @Test
    void an_items_files_are_its_url_its_medium_and_those_in_its_text_once_each() {
        medium("m1", SEEN);
        medium("m2", HIDDEN);
        assertThat(documents().ofItem(item("media:m1", "<p>[[media:m1]] [[media:m2]]</p>")))
                .extracting(d -> d.ref().fileId()).containsExactly(SEEN, HIDDEN);
        assertThat(documents().ofItem(item("https://example.com/a.pdf", "<p>x</p>"))).isEmpty();
    }

    @Test
    void the_list_is_what_the_visibility_function_gives_each_file_once() {
        visible.add(new ContentIndex.ExternalReference("new", "Nuevo", docUrl(SEEN)));
        visible.add(new ContentIndex.ExternalReference("old", "Viejo", docUrl(SEEN)));
        visible.add(new ContentIndex.ExternalReference("other", "Otro", "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn"));
        assertThat(documents().visibleTo(EmployeeAttributes.NONE)).extracting(DriveDocuments.Document::itemId).containsExactly("new");
    }
}
