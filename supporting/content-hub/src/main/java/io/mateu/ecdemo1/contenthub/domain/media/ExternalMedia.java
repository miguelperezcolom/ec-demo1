package io.mateu.ecdemo1.contenthub.domain.media;

import io.mateu.ecdemo1.contenthub.domain.drive.DriveFileRef;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What is known of an external medium: which provider, and the id that gives its embeddable player.
 * The editor may paste the URL or the provider's whole "embed" HTML; only the provider and the id are
 * kept — never the pasted HTML, which could carry anything. The page then embeds the provider's official
 * player from a closed list of hosts ({@link #embedUrl()}), and anything else is a link.
 */
public record ExternalMedia(Provider provider, String url, String videoId) {

    public enum Provider { YOUTUBE, VIMEO, DRIVE, OTHER }

    static final Pattern YOUTUBE = Pattern.compile(
            "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^\"'\\s]*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{6,})");
    static final Pattern VIMEO = Pattern.compile("vimeo\\.com/(?:video/)?(\\d+)");
    static final Pattern IFRAME_SRC = Pattern.compile("<iframe[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    /** @param input a URL, or a provider's embed HTML ({@code <iframe src="…">}) */
    public static ExternalMedia of(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Hace falta la URL");
        }
        var u = input.trim();
        var iframe = IFRAME_SRC.matcher(u);
        if (iframe.find()) {
            u = iframe.group(1).trim();
        }
        if (u.startsWith("//")) {
            u = "https:" + u;
        }
        var scheme = Optional.ofNullable(URI.create(u.replace(" ", "%20")).getScheme()).orElse("");
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Solo URLs http(s) o el código de inserción del proveedor");
        }
        var yt = YOUTUBE.matcher(u);
        if (yt.find()) {
            return new ExternalMedia(Provider.YOUTUBE, "https://www.youtube.com/watch?v=" + yt.group(1), yt.group(1));
        }
        var vimeo = VIMEO.matcher(u);
        if (vimeo.find()) {
            return new ExternalMedia(Provider.VIMEO, "https://vimeo.com/" + vimeo.group(1), vimeo.group(1));
        }
        // any Drive file or Doc/Sheet/Slides: a reference to the one version in Drive, never a copy
        var drive = DriveFileRef.parse(u);
        if (drive.isPresent()) {
            return new ExternalMedia(Provider.DRIVE, drive.get().canonicalUrl(), drive.get().fileId());
        }
        return new ExternalMedia(Provider.OTHER, u, null);
    }

    /** The provider's official player, built from the id — never from what was pasted. */
    public Optional<String> embedUrl() {
        return switch (provider) {
            case YOUTUBE -> Optional.of("https://www.youtube-nocookie.com/embed/" + videoId);
            case VIMEO -> Optional.of("https://player.vimeo.com/video/" + videoId);
            case DRIVE -> Optional.of(new DriveFileRef(videoId, null).previewUrl());
            case OTHER -> Optional.empty();
        };
    }

    /** The Drive file it references, for a Drive medium. */
    public Optional<DriveFileRef> drive() {
        return provider == Provider.DRIVE ? DriveFileRef.parse(url) : Optional.empty();
    }

    /** A thumbnail anyone can load, when the provider gives one by id. */
    public Optional<String> thumbnail() {
        return provider == Provider.YOUTUBE ? Optional.of("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg")
                : Optional.empty();
    }
}
