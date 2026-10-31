package io.mateu.ecdemo1.contenthub.domain.item;

/** One language's text of an item. The body is HTML, already sanitised by whoever built it. */
public record Translation(String title, String summary, String body) {

    public Translation {
        title = title == null ? "" : title.trim();
        summary = summary == null ? "" : summary.trim();
        body = body == null ? "" : body;
    }
}
