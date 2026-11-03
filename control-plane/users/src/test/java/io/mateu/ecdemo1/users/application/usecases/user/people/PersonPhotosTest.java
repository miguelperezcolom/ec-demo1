package io.mateu.ecdemo1.users.application.usecases.user.people;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonPhotosTest {

    static byte[] png(int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void whatever_comes_is_served_as_a_small_square_jpeg_of_our_own() throws IOException {
        var photo = ImageIO.read(new ByteArrayInputStream(PersonPhotos.square(png(800, 600))));

        assertThat(photo.getWidth()).isEqualTo(PersonPhotos.SIDE);
        assertThat(photo.getHeight()).isEqualTo(PersonPhotos.SIDE);
        // transparency, on white
        assertThat(photo.getRGB(10, 10) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void what_is_not_an_image_is_refused_whatever_it_says_it_is() {
        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PersonPhotos.square(svg)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es una imagen");
        assertThatThrownBy(() -> PersonPhotos.square("hola".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_enormous_image_is_refused_before_decoding_it() {
        assertThatThrownBy(() -> PersonPhotos.square(png(PersonPhotos.MAX_SIDE + 1, 1))).hasMessageContaining("demasiado grande");
    }

    @Test
    void too_many_bytes_or_a_malformed_data_uri_are_refused() {
        var big = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[PersonPhotos.MAX_BYTES + 1]);

        assertThatThrownBy(() -> PersonPhotos.decoded(big)).hasMessageContaining("5 MB");
        assertThatThrownBy(() -> PersonPhotos.decoded("data:image/png")).isInstanceOf(IllegalArgumentException.class);
    }
}
