package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.MediaCommands;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.FileUpload;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.concurrent.Callable;

/**
 * A medium of the library: a small file uploaded here (images, PDF, audio, video up to the limit), or —
 * for anything bigger — the URL of where it lives, or the provider's embed code (only its provider and id
 * are kept). Embedded in content with a MEDIA block or {@code [[media:id]]}.
 */
@Service
@Scope("prototype")
public class MediaForm implements SimpleCrud.CrudForm, VisibilitySupplier {

    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @ReadOnly
    @HiddenInCreate
    @Label("Para incrustarlo, pega esto en el cuerpo")
    String mark;
    @Label("Título")
    String title;

    @Section("Subir un fichero (hasta 20 MB)")
    @FileUpload(accept = "image/*,application/pdf,audio/*,video/*")
    @Label("Fichero")
    String file;

    @Section("…o indicar dónde está (YouTube, Vimeo, Drive, cualquier URL)")
    @Label("URL o código de inserción")
    @Stereotype(FieldStereotype.textarea)
    String externalUrl;

    @Section("Vista previa")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    Callable<Component> preview = this::preview;

    @ReadOnly
    @HiddenInCreate
    @Label("Detalles")
    String details;

    final MediaCommands commands;
    final MediaRepository media;
    final ContentRenderer renderer;
    final Readers readers;

    public MediaForm(MediaCommands commands, MediaRepository media, ContentRenderer renderer, Readers readers) {
        this.commands = commands;
        this.media = media;
        this.renderer = renderer;
        this.readers = readers;
    }

    public MediaForm load(MediaAsset a) {
        id = a.id();
        title = a.title();
        mark = "[[media:" + a.id() + "]]";
        externalUrl = a.externalUrl();
        details = a.kind().label() + (a.size() > 0 ? " · " + (a.size() / 1024) + " KB · " + a.contentType() : "")
                + " · subido por " + a.uploadedBy();
        return this;
    }

    Component preview() {
        if (id == null) {
            return Html.block("");
        }
        return Html.block(media.find(id).map(a -> renderer.medium(a, "/_content-hub", MediaUrls.EDITOR)).orElse(""));
    }

    @Override
    public String create(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        if (file != null && file.startsWith("data:")) {
            var comma = file.indexOf(',');
            var meta = file.substring(5, comma);
            var contentType = meta.split(";")[0];
            var bytes = Base64.getMimeDecoder().decode(file.substring(comma + 1));
            return commands.upload(reader, title, title == null || title.isBlank() ? "fichero" : title, contentType, bytes);
        }
        if (externalUrl != null && !externalUrl.isBlank()) {
            return commands.external(reader, title, externalUrl);
        }
        throw new IllegalArgumentException("Sube un fichero o indica una URL");
    }

    @Override
    public void save(HttpRequest httpRequest) {
        throw new IllegalStateException("Un medio no se modifica: sube uno nuevo");
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return id != null && ("file".equals(fieldName) || "externalUrl".equals(fieldName));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nuevo medio" : title;
    }
}
