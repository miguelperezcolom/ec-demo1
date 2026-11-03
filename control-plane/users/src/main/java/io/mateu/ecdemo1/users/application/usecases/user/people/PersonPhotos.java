package io.mateu.ecdemo1.users.application.usecases.user.people;

import io.mateu.ecdemo1.users.application.out.PersonPhotoRepository;
import io.mateu.ecdemo1.users.application.out.UserRepository;
import io.mateu.ecdemo1.users.application.usecases.user.publish.UserChangePublisher;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * A person's photo. What arrives is decoded and drawn again as a small square JPEG, so what gets served is an
 * image we made: never what was uploaded, whatever it says it is. Its address is unguessable and changes with
 * each photo, because an {@code <img>} carries no token: it's what the portal's media do too.
 */
@Service
@RequiredArgsConstructor
public class PersonPhotos {

    public static final String BASE = "/_people/photos/";
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_SIDE = 8000;
    static final int SIDE = 320;

    final UserRepository users;
    final PersonPhotoRepository photos;
    final UserChangePublisher publisher;

    /**
     * What a photo field left. Only a new image (a data URI) changes anything: an empty field may just be a renderer
     * that didn't send it, and saving a form must never lose a photo. Taking it away is {@link #remove}.
     */
    @Transactional
    public void replace(String userId, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.startsWith("data:")) {
            set(userId, value);
        } else if (!value.equals(currentOf(userId))) {
            // the field also lets one type an address: a photo is always ours, so it has to be uploaded
            throw new IllegalArgumentException("Sube la foto desde tu equipo: no se admite una dirección");
        }
    }

    @Transactional
    public void remove(String userId) {
        photos.deleteOfUser(userId);
        pointTo(userId, null);
    }

    private void set(String userId, String dataUri) {
        var content = square(decoded(dataUri));
        var token = UUID.randomUUID().toString().replace("-", "");
        photos.deleteOfUser(userId);
        photos.save(new PersonPhotoRepository.Photo(token, userId, "image/jpeg", content));
        pointTo(userId, BASE + token);
    }

    private String currentOf(String userId) {
        return users.findById(new UserId(userId)).map(u -> u.getProfile().photoUrl()).orElse(null);
    }

    private void pointTo(String userId, String url) {
        var user = users.findById(new UserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("No existe la persona " + userId));
        if (java.util.Objects.equals(url, user.getProfile().photoUrl())) {
            return;
        }
        user.update(user.getName(), user.getEmail(), user.getGroups(), user.getRoles(), user.getAttributes(),
                user.getProfile().withPhotoUrl(url));
        users.save(user);
        publisher.publish(user);
    }

    static byte[] decoded(String dataUri) {
        var comma = dataUri.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Esa imagen no se puede leer");
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(dataUri.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Esa imagen no se puede leer");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("La foto no puede pasar de 5 MB");
        }
        return bytes;
    }

    /** Its centre, as a {@value #SIDE}px square, over white where it's transparent. */
    static byte[] square(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Eso no es una imagen: usa una foto JPEG o PNG");
            }
            var reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input);
                // asked before decoding: a small file can declare an enormous image
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) {
                    throw new IllegalArgumentException("La foto es demasiado grande: como mucho " + MAX_SIDE + " píxeles de lado");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
            var side = Math.min(source.getWidth(), source.getHeight());
            var out = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
            var graphics = out.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, SIDE, SIDE);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                var x = (source.getWidth() - side) / 2;
                var y = (source.getHeight() - side) / 2;
                graphics.drawImage(source, 0, 0, SIDE, SIDE, x, y, x + side, y + side, null);
            } finally {
                graphics.dispose();
            }
            var jpeg = new ByteArrayOutputStream();
            ImageIO.write(out, "jpeg", jpeg);
            return jpeg.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Esa imagen no se puede leer");
        }
    }
}
