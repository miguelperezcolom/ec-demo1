package io.mateu.ecdemo1.contenthub.domain.drive;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A reference to a file in Google Drive — never a copy: the intranet keeps the file's id and what kind of
 * file it is, and shows Google's own live preview and links, so there is a single version, the one in Drive.
 *
 * <p>Parsed from whatever an editor pastes: the share link of any Drive file
 * ({@code drive.google.com/file/d/{id}/view}, {@code open?id=}, {@code uc?id=}), the editor URL of a Doc,
 * Sheet, Slides, Form or Drawing ({@code docs.google.com/document/d/{id}/edit…}), with or without a
 * {@code /u/0/} account or a {@code /a/domain/} prefix, or the iframe Google gives to embed it. A folder or a
 * "published to the web" copy ({@code /d/e/…/pub}) is not a file reference and is refused.
 */
public record DriveFileRef(String fileId, Type type) {

    /** What kind of file, by the URL it was pasted with: it decides the editor's URL. */
    public enum Type {
        DOCUMENT("document", "Documento de Google"),
        SPREADSHEET("spreadsheets", "Hoja de cálculo de Google"),
        PRESENTATION("presentation", "Presentación de Google"),
        FORM("forms", "Formulario de Google"),
        DRAWING("drawings", "Dibujo de Google"),
        /** Any other file kept in Drive (a PDF, an image, an Office file…): previewed, not edited online. */
        FILE(null, "Fichero de Drive");

        final String path;
        final String label;

        Type(String path, String label) {
            this.path = path;
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** The type a Drive mimeType stands for (Drive API v3), FILE for anything not native. */
        public static Type ofMimeType(String mimeType) {
            if (mimeType == null) {
                return FILE;
            }
            return switch (mimeType) {
                case "application/vnd.google-apps.document" -> DOCUMENT;
                case "application/vnd.google-apps.spreadsheet" -> SPREADSHEET;
                case "application/vnd.google-apps.presentation" -> PRESENTATION;
                case "application/vnd.google-apps.form" -> FORM;
                case "application/vnd.google-apps.drawing" -> DRAWING;
                default -> FILE;
            };
        }
    }

    static final String ID = "([A-Za-z0-9_-]{10,})";
    static final Pattern IFRAME_SRC = Pattern.compile("<iframe[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    /** docs.google.com/[a/domain/][u/N/]{document|spreadsheets|presentation|forms|drawings}/[u/N/]d/{id} */
    static final Pattern DOCS = Pattern.compile("^https?://docs\\.google\\.com/(?:a/[^/]+/)?(?:u/\\d+/)?"
            + "(document|spreadsheets|presentation|forms|drawings)/(?:u/\\d+/)?d/(e/)?" + ID, Pattern.CASE_INSENSITIVE);
    /** {drive|docs}.google.com/[a/domain/][u/N/]file/[u/N/]d/{id} */
    static final Pattern DRIVE_FILE = Pattern.compile("^https?://(?:drive|docs)\\.google\\.com/(?:a/[^/]+/)?(?:u/\\d+/)?"
            + "file/(?:u/\\d+/)?d/" + ID, Pattern.CASE_INSENSITIVE);
    /** drive.google.com/{open|uc}?…id={id} (also docs.google.com/uc) */
    static final Pattern DRIVE_QUERY = Pattern.compile("^https?://(?:drive|docs)\\.google\\.com/(?:a/[^/]+/)?"
            + "(?:open|uc)\\?(?:[^#]*&)?id=" + ID, Pattern.CASE_INSENSITIVE);
    static final Pattern FOLDER = Pattern.compile("^https?://drive\\.google\\.com/.*(?:folders/|/folderview)",
            Pattern.CASE_INSENSITIVE);

    public DriveFileRef {
        if (fileId == null || !fileId.matches("[A-Za-z0-9_-]{10,}")) {
            throw new IllegalArgumentException("Id de fichero de Drive no válido");
        }
        if (type == null) {
            type = Type.FILE;
        }
    }

    /** Whether the text is a Google Drive / Docs URL at all (a file or not), so it is not taken for another link. */
    public static boolean isGoogleUrl(String input) {
        var u = clean(input);
        return u != null && u.toLowerCase(Locale.ROOT).matches("^https?://(drive|docs)\\.google\\.com/.*");
    }

    /** The file the text points at, or empty when it is no Drive file URL (another link, a folder, a published copy). */
    public static Optional<DriveFileRef> parse(String input) {
        var u = clean(input);
        if (u == null) {
            return Optional.empty();
        }
        var docs = DOCS.matcher(u);
        if (docs.find()) {
            if (docs.group(2) != null) {
                return Optional.empty(); // "publish to the web": an id of the published copy, not of the file
            }
            var kind = docs.group(1).toLowerCase(Locale.ROOT);
            for (var t : Type.values()) {
                if (kind.equals(t.path)) {
                    return Optional.of(new DriveFileRef(docs.group(3), t));
                }
            }
        }
        var file = DRIVE_FILE.matcher(u);
        if (file.find()) {
            return Optional.of(new DriveFileRef(file.group(1), Type.FILE));
        }
        if (FOLDER.matcher(u).find()) {
            return Optional.empty();
        }
        var query = DRIVE_QUERY.matcher(u);
        if (query.find()) {
            return Optional.of(new DriveFileRef(query.group(1), Type.FILE));
        }
        return Optional.empty();
    }

    /** Like {@link #parse}, saying why when it is not a file. */
    public static DriveFileRef of(String input) {
        return parse(input).orElseThrow(() -> new IllegalArgumentException(
                FOLDER.matcher(String.valueOf(clean(input))).find()
                        ? "Es una carpeta de Drive: enlaza un fichero"
                        : "No es la URL de un fichero de Google Drive, Docs, Sheets o Slides"));
    }

    static String clean(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        var u = input.trim();
        var iframe = IFRAME_SRC.matcher(u);
        if (iframe.find()) {
            u = iframe.group(1).trim();
        }
        if (u.startsWith("//")) {
            u = "https:" + u;
        }
        if (u.regionMatches(true, 0, "drive.google.com/", 0, 17) || u.regionMatches(true, 0, "docs.google.com/", 0, 16)) {
            u = "https://" + u; // pasted without its scheme
        }
        return u.replace("&amp;", "&");
    }

    /** The same reference with the type Drive says it has (a FILE pasted by its share link may be a Doc). */
    public DriveFileRef withMimeType(String mimeType) {
        var t = Type.ofMimeType(mimeType);
        return t == Type.FILE ? this : new DriveFileRef(fileId, t);
    }

    /** Google's live preview, what embed.js puts in an iframe: the same for every kind of file. */
    public String previewUrl() {
        return "https://drive.google.com/file/d/" + fileId + "/preview";
    }

    /** Where the reader opens it in Drive (Google redirects to the right viewer). */
    public String openUrl() {
        return type.path == null ? "https://drive.google.com/file/d/" + fileId + "/view"
                : "https://drive.google.com/open?id=" + fileId;
    }

    /** Google's editor for a native file; empty for any other file, which is not edited online. */
    public Optional<String> editUrl() {
        return type.path == null ? Optional.empty()
                : Optional.of("https://docs.google.com/" + type.path + "/d/" + fileId + "/edit");
    }

    /** The URL kept for it: the editor's for a native file, the viewer's for any other. */
    public String canonicalUrl() {
        return editUrl().orElse(openUrl());
    }
}
