package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PeoplePictureTest {

    static Employee of(String name, String photo) {
        return new Employee("eva", name, "eva@x", true, null, null, null, java.util.Map.of(), photo);
    }

    @Test
    void whoHasAPhotoShowsIt() {
        assertThat(People.picture(of("Eva García", "/_people/photos/abc"))).isEqualTo("/_people/photos/abc");
    }

    @Test
    void whoHasNoneShowsTheirInitialsDrawnAsAnImage() {
        var picture = People.picture(of("Eva García", null));

        assertThat(picture).startsWith("data:image/svg+xml;base64,");
        assertThat(new String(Base64.getDecoder().decode(picture.substring(picture.indexOf(',') + 1)), StandardCharsets.UTF_8))
                .contains(">EG</text>");
    }

    @Test
    void aNameCannotWriteIntoTheImage() {
        var picture = People.picture(of("<x y", null));

        assertThat(new String(Base64.getDecoder().decode(picture.substring(picture.indexOf(',') + 1)), StandardCharsets.UTF_8))
                .doesNotContain("<XY").contains("&lt;");
    }
}
