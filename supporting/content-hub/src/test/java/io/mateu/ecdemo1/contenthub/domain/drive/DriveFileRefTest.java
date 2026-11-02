package io.mateu.ecdemo1.contenthub.domain.drive;

import io.mateu.ecdemo1.contenthub.domain.media.ExternalMedia;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriveFileRefTest {

    static final String ID = "1AbC_dEf-GhIjKlMnOpQ";

    @ParameterizedTest
    @CsvSource({
            "https://drive.google.com/file/d/" + ID + "/view?usp=sharing, FILE",
            "https://drive.google.com/file/d/" + ID + ", FILE",
            "https://drive.google.com/file/d/" + ID + "/preview, FILE",
            "https://drive.google.com/file/u/1/d/" + ID + "/view, FILE",
            "https://drive.google.com/u/0/file/d/" + ID + "/view, FILE",
            "https://drive.google.com/a/riu.com/file/d/" + ID + "/view, FILE",
            "https://docs.google.com/file/d/" + ID + "/edit, FILE",
            "https://drive.google.com/open?id=" + ID + ", FILE",
            "https://drive.google.com/open?usp=sharing&id=" + ID + ", FILE",
            "https://drive.google.com/uc?export=download&id=" + ID + ", FILE",
            "https://docs.google.com/uc?id=" + ID + ", FILE",
            "drive.google.com/file/d/" + ID + "/view, FILE",
            "https://docs.google.com/document/d/" + ID + "/edit?usp=sharing, DOCUMENT",
            "https://docs.google.com/document/d/" + ID + "/preview, DOCUMENT",
            "https://docs.google.com/document/u/2/d/" + ID + "/edit#heading=h.x, DOCUMENT",
            "https://docs.google.com/a/riu.com/document/d/" + ID + "/edit, DOCUMENT",
            "docs.google.com/document/d/" + ID + "/edit, DOCUMENT",
            "https://docs.google.com/spreadsheets/d/" + ID + "/edit#gid=0, SPREADSHEET",
            "https://docs.google.com/spreadsheets/u/0/d/" + ID + "/htmlview, SPREADSHEET",
            "https://docs.google.com/presentation/d/" + ID + "/edit#slide=id.p, PRESENTATION",
            "https://docs.google.com/forms/d/" + ID + "/edit, FORM",
            "https://docs.google.com/drawings/d/" + ID + "/edit, DRAWING",
    })
    void every_shape_an_editor_may_paste(String url, DriveFileRef.Type type) {
        var ref = DriveFileRef.parse(url).orElseThrow();
        assertThat(ref.fileId()).isEqualTo(ID);
        assertThat(ref.type()).isEqualTo(type);
    }

    @Test
    void the_iframe_google_gives_to_embed() {
        var ref = DriveFileRef.parse("<iframe width=\"640\" src=\"https://docs.google.com/document/d/" + ID
                + "/preview\"></iframe>").orElseThrow();
        assertThat(ref.type()).isEqualTo(DriveFileRef.Type.DOCUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn",
            "https://drive.google.com/drive/u/0/folders/1AbCdEfGhIjKlMn",
            "https://docs.google.com/document/d/e/2PACX-1vQabcdefghijkl/pub",
            "https://example.com/file/d/1AbCdEfGhIjKlMn/view",
            "https://evil.example/?x=https://drive.google.com/file/d/1AbCdEfGhIjKlMn/view",
            "no es una url",
            "",
    })
    void what_is_no_file_is_no_reference(String url) {
        assertThat(DriveFileRef.parse(url)).isEmpty();
    }

    @Test
    void the_urls_it_gives() {
        var doc = new DriveFileRef(ID, DriveFileRef.Type.DOCUMENT);
        assertThat(doc.previewUrl()).isEqualTo("https://drive.google.com/file/d/" + ID + "/preview");
        assertThat(doc.editUrl()).hasValue("https://docs.google.com/document/d/" + ID + "/edit");
        assertThat(new DriveFileRef(ID, DriveFileRef.Type.SPREADSHEET).editUrl())
                .hasValue("https://docs.google.com/spreadsheets/d/" + ID + "/edit");
        var pdf = new DriveFileRef(ID, DriveFileRef.Type.FILE);
        assertThat(pdf.editUrl()).isEmpty();
        assertThat(pdf.openUrl()).isEqualTo("https://drive.google.com/file/d/" + ID + "/view");
    }

    @Test
    void what_drive_says_it_is_upgrades_a_share_link() {
        var ref = new DriveFileRef(ID, DriveFileRef.Type.FILE).withMimeType("application/vnd.google-apps.spreadsheet");
        assertThat(ref.type()).isEqualTo(DriveFileRef.Type.SPREADSHEET);
        assertThat(new DriveFileRef(ID, DriveFileRef.Type.DOCUMENT).withMimeType(null).type())
                .isEqualTo(DriveFileRef.Type.DOCUMENT);
    }

    @Test
    void a_drive_medium_keeps_a_reference_and_previews_through_the_allowed_host() {
        var medium = ExternalMedia.of("https://docs.google.com/document/d/" + ID + "/edit?usp=sharing");
        assertThat(medium.provider()).isEqualTo(ExternalMedia.Provider.DRIVE);
        assertThat(medium.url()).isEqualTo("https://docs.google.com/document/d/" + ID + "/edit");
        assertThat(medium.embedUrl()).hasValue("https://drive.google.com/file/d/" + ID + "/preview");
    }

    @Test
    void the_library_refuses_a_folder_saying_why_and_names_an_untitled_file() {
        assertThatThrownBy(() -> MediaAsset.external("m", "t", "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn",
                "eva", Instant.EPOCH)).hasMessageContaining("carpeta");
        var asset = MediaAsset.external("m", "", "https://docs.google.com/presentation/d/" + ID + "/edit", "eva", Instant.EPOCH);
        assertThat(asset.title()).isEqualTo("Presentación de Google");
        // any other link stays what it was
        assertThat(ExternalMedia.of("https://example.com/x").provider()).isEqualTo(ExternalMedia.Provider.OTHER);
    }
}
