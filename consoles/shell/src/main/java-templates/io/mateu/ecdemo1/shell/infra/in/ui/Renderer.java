package io.mateu.ecdemo1.shell.infra.in.ui;

/**
 * Which renderer this build of the console carries — filled in by Maven (src/main/java-templates,
 * filtered): empty for the default Vaadin build, " · Redwood" for {@code -Predwood}. A constant,
 * because the page title is an annotation.
 */
final class Renderer {

    /** Appended to the browser tab's title, so the two consoles of a plane tell themselves apart. */
    static final String TITLE_SUFFIX = "@console.title.suffix@";

    private Renderer() {
    }
}
